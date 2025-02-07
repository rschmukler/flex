(ns town.lilac.flex-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.flex :as f])
  #?(:clj (:import clojure.lang.ExceptionInfo)))

(deftest linear
  (let [*calls (atom [])
        A (f/source 2)
        B (f/signal (* @A @A))]
    (is (= 2 @A))
    (is (not (f/connected? B)))
    (let [Z (f/listen B #(swap! *calls conj %))]
      (is (= 2 @A))
      (is (= 4 @B))
      (is (= [] @*calls))
      (A 3)
      (is (= 3 @A))
      (is (= 9 @B))
      (is (= [9] @*calls))
      (f/dispose! Z)
      (A 4)
      (is (= 4 @A))
      (is (not (f/connected? B)))
      (is (= [9] @*calls))))
  (let [*calls (atom [])
        A (f/source 2)
        B (f/signal (* @A @A))]
    (is (= 2 @A))
    (is (not (f/connected? B)))
    (let [fx (f/effect [] (swap! *calls conj @B))]
      (is (= 2 @A))
      (is (= 4 @B))
      (is (= [4] @*calls))
      (A 3)
      (is (= 3 @A))
      (is (= 9 @B))
      (is (= [4 9] @*calls))
      (f/dispose! fx)
      (A 4)
      (is (= 4 @A))
      (is (not (f/connected? B)))
      (is (= [4 9] @*calls)))))

(deftest dirty-diamond
  (let [*calls (atom [])
        A (f/source 2)
        B (f/signal (* @A @A))
        C (f/signal (+ @A 2))
        D (f/signal (* @C @C))
        fx (f/effect [] (swap! *calls conj [@B @D]))]
    (is (= 2 @A))
    (is (= 4 @B))
    (is (= 4 @C))
    (is (= 16 @D))
    (is (= [[4 16]] @*calls))
    (A 3)
    (is (= 3 @A))
    (is (= 9 @B))
    (is (= 5 @C))
    (is (= 25 @D))
    (is (= [[4 16] [9 25]] @*calls))
    (f/dispose! fx)
    (A 4)
    (is (= 4 @A))
    (is (not (f/connected? B)))
    (is (not (f/connected? C)))
    (is (not (f/connected? D)))
    (is (= [[4 16] [9 25]] @*calls))))

(deftest effect-cleanup
  (let [*calls (atom [])
        *cleanup-calls (atom 0)
        A (f/source 2)
        fx (f/effect
                 ([]
                  (swap! *calls conj @A)
                  #(swap! *cleanup-calls inc))
                 ([cleanup]
                  (swap! *calls conj @A)
                  ;; first cleanup is nil
                  (cleanup)
                  #(swap! *cleanup-calls inc)))]
    (is (= [2] @*calls))
    (is (= 0 @*cleanup-calls))
    (A 3)
    (is (= [2 3] @*calls))
    (is (= 1 @*cleanup-calls))
    (f/dispose! fx)
    (is (= [2 3] @*calls))
    (is (= 2 @*cleanup-calls))))

(deftest on-dispose
  (let [*calls (atom [])
        *disposed (atom 0)
        A (f/source 0)
        B (f/on-dispose (f/signal (* @A @A)) (fn [_] (swap! *disposed inc)))
        fx (f/effect [] (swap! *calls conj @B))]
    (is (= 0 @*disposed))
    (is (= [0] @*calls))
    (A 2)
    (is (= 0 @*disposed))
    (is (= [0 4] @*calls))
    (f/dispose! fx)
    (is (= 1 @*disposed))
    (is (= [0 4] @*calls))
    (f/run! fx)
    (is (= 1 @*disposed))
    (is (= [0 4 4] @*calls))
    (f/dispose! fx)
    (is (= 2 @*disposed))
    (is (= [0 4 4] @*calls))))

(deftest conditional
  (testing "conditional sources"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/source 10)
          C (f/source 100)
          D (f/signal (if (even? @A)
                        (inc @B)
                        (inc @C)))
          fx (f/effect [] (swap! *calls conj @D))]
      (is (= [11] @*calls))
      (B 20)
      (C 200)
      (is (= [11 21] @*calls))
      (A 1)
      (is (= [11 21 201] @*calls))
      (B 30)
      (C 300)
      (is (= [11 21 201 301] @*calls))
      (A 2)
      (B 40) (C 400)
      (is (= [11 21 201 301 31 41] @*calls))
      (f/dispose! fx)
      (A 3)
      (B 50) (C 500)
      (is (= [11 21 201 301 31 41] @*calls))))
  (testing "conditional signals"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (* 10 @A))
          C (f/signal (* 100 @A))
          D (f/signal (if (even? @A)
                        (inc @B)
                        (inc @C)))
          fx (f/effect [] (swap! *calls conj @D))]
      (is (= [1] @*calls))
      (is (not (f/connected? C)))
      (A 1)
      (is (= [1 101] @*calls))
      (is (not (f/connected? B)))
      (A 2)
      (is (= [1 101 21] @*calls))
      (f/dispose! fx)
      (is (not (f/connected? B)))
      (is (not (f/connected? C)))
      (is (not (f/connected? D)))
      (A 3)
      (is (= [1 101 21] @*calls))))
  (testing "order"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (+ @A 10))
          C (f/signal (- @B 10))
          D (f/signal (let [a @A]
                        (if (> a 0)
                          (let [c @C]
                            (+ a c))
                          a)))
          _fx (f/effect [] (swap! *calls conj @D))]
      (is (= [0] @*calls))
      (A 1)
      (A 2)
      (is (= [0 2 4] @*calls)))))

(deftest transaction
  (let [*calls (atom [])
        A (f/source 0)
        B (f/source 0)
        _fx (f/effect [] (swap! *calls conj [@A @B]))]
    (is (= [[0 0]] @*calls))
    (f/batch-send! (fn []
                     (A 1)
                     (is (= 1 @A))
                     (is (= 0 @B))
                     (B 1)
                     (is (= 1 @B))
                     (is (= 1 @A))
                     (A 2)
                     (is (= [[0 0]] @*calls))))
    (is (= [[0 0] [2 1]] @*calls)))
  (testing "exceptions"
    (let [*calls (atom [])
          A (f/source 1)
          B (f/source 1)
          C (f/signal #?(:clj (/ @A @B)
                         :cljs (let [x (/ @A @B)]
                                 (when (= ##Inf x)
                                   (throw (ex-info "Divide by zero" {})))
                                 x)))
          _fx (f/effect [] (swap! *calls conj @C))]
      (is (= [1] @*calls))
      (is (thrown? #?(:clj ExceptionInfo :cljs js/Error)
                   (f/batch-send! (fn []
                                    (A 2)
                                    (B 2)
                                    (throw (ex-info "oh no" {}))))))
      (is (= 1 @A))
      (is (= 1 @B))
      (is (= [1] @*calls))
      (f/batch-send! (fn []
                       (A 2)
                       (B 0)))
      (is (= 2 @A))
      (is (= 0 @B))
      (is (= [1] @*calls) "effect ")
      (f/batch-send! (fn []
                       (A 4)
                       (B 0) ; since this is updated again, no error triggered
                       (B 2)))
      (is (= [1 2] @*calls))))
  (testing "signal computations are not tx local"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (* @A @A))
          _fx (f/effect [] (swap! *calls conj @B))]
      (f/batch
       (A 2)
       (is (= 0 @B))
       (A 3))
      (is (= [0 9] @*calls))
      (is (thrown? #?(:clj ExceptionInfo
                      :cljs js/Error)
                   (f/batch
                    (A 4)
                    (is (= 9 @B))
                    (throw (ex-info "oh no" {})))))
      (is (= [0 9] @*calls))
      (is (= 9 @B))))
  (testing "nested"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (* @A @A))
          _fx (f/effect [] (swap! *calls conj @B))]
      (f/batch
       (A 1)
       (f/send! A 2)
       (A inc))
      (is (= [0 9] @*calls))
      (f/batch
       (A 1)
       (f/batch
        (A 2)
        (f/batch
         (A inc))
        (A inc))
       (A inc))
      (is (= [0 9 25] @*calls))))
  (testing "nested error"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/signal (* @A @A))
          _fx (f/effect [] (swap! *calls conj @B))]
      (is (thrown?
           #?(:clj ExceptionInfo :cljs js/Error)
           (f/batch
            (A 1)
            (f/send! A 2)
            (throw (ex-info "oh no" {}))
            (A inc))))
      (is (= [0] @*calls))
      (f/batch
       (A 1)
       (is (thrown?
            #?(:clj ExceptionInfo :cljs js/Error)
            (f/batch
             (A 2)
             (throw (ex-info "oh no" {}))
             (A 3))))
       (is (= 1 @A))
       (A inc))
      (is (= [0 4] @*calls)))))

(deftest signal-error
  (let [*calls (atom [])
        *errors (atom 0)
        A (f/source 1)
        B (f/source 1)
        C (f/signal #?(:clj (/ @A @B)
                       :cljs (let [x (/ @A @B)]
                               (when (= ##Inf x)
                                 (throw (ex-info "Divide by zero" {})))
                               x)))
        _fx (f/effect
                  []
                  (try (swap! *calls conj @C)
                       (catch #?(:clj ArithmeticException :cljs js/Error) _e
                         (swap! *errors inc))))]
    (B 0)
    (is (= [1] @*calls) "effect does not run after only dependent errors")
    (is (= 1 @*errors))
    (is (thrown? #?(:clj ArithmeticException :cljs js/Error) @C)))
  (testing "diamond"
    (let [*calls (atom [])
          *errors (atom 0)
          A (f/source 1)
          B (f/source 1)
          D (f/signal (+ @A @B))
          C (f/signal #?(:clj (/ @A @B)
                         :cljs (let [x (/ @A @B)]
                                 (when (= ##Inf x)
                                   (throw (ex-info "Divide by zero" {})))
                                 x)))
          _disopse (-> (f/effect
                        []
                        (swap! *calls conj [@C @D]))
                       (f/on-error (fn [_e] (swap! *errors inc))))]
      (is (= 2 @D))
      (B 0)
      (is (= @B 0) "B is updated")
      (is (thrown?
           #?(:clj ArithmeticException :cljs js/Error)
           @C) "C throws on deref")
      (is (= 1 @D) "D is next val")
      (is (= [[1 2]] @*calls) "effect catches")
      (is (= 1 @*errors) "error is caught in effect"))))

(deftest skip
  (let [*calls (atom [])
        A (f/source 0)
        B (f/signal (inc @A))
        _ (f/effect [] (swap! *calls conj @B))]
    (A 1)
    (A 2)
    (f/skip (A 3) (A 4))
    (A 5)
    (is (= [1 2 3 6] @*calls))))

(deftest untrack
  (let [*calls (atom [])
        A (f/source 0)
        B (f/source 0)
        C (f/signal [@A (f/untrack @B)])
        _ (f/effect [] (swap! *calls conj @C))]
    (A 1)
    (B 1)
    (A 2)
    (is (= [[0 0] [1 0] [2 1]] @*calls)))
  (testing "conditional"
    (let [*calls (atom [])
          A (f/source 0)
          B (f/source 0)
          C (f/signal (if (even? @A)
                        [@A @B]
                        [@A (f/untrack @B)]))
          _ (f/effect [] (swap! *calls conj @C))]
      (A 1)
      (B 1)
      (A 2)
      (is (= [[0 0] [1 0] [2 1]] @*calls))
      (B 2)
      (is (= [[0 0] [1 0] [2 1] [2 2]] @*calls))
      (A 3)
      (B 3)
      (is (= [[0 0] [1 0] [2 1] [2 2] [3 2]] @*calls)))))

(deftest disconnected-test
  (let [*calls (atom 0)
        A (f/source 0)
        B (f/signal
           (swap! *calls inc)
           (inc @A))
        C (f/signal (* @B @B))]
    (is (= 1 @C))
    (is (= 1 @C))
    (is (= 4 @*calls))
    (is (= f/sentinel (:cache (f/dump B))))
    (is (not (f/connected? B)))
    (is (not (f/connected? A)))
    (let [_fx (f/effect [] @B)]
      (is (= 1 @C))
      (is (= 1 @C))
      (is (= 5 @*calls)))))

(deftest laziness
  (let [src (f/source 5)
        A (f/signal (* @src @src))
        B (f/signal (map #(+ @A %) (range 5)))
        fx (f/effect [] (prn @B))]
    ))

(comment
  (t/run-tests))
