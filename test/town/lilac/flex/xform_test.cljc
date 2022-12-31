(ns town.lilac.flex.xform-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.flex :as f]
   [town.lilac.flex.xform :as xf]))

(deftest reduce-test
  (let [*calls (atom [])
        A (f/source 0)
        B (xf/reduce conj [] A)
        Z (f/effect [_] (swap! *calls conj @B))
        dispose (Z)]
    (is (= [[0]] @*calls))
    (A 1)
    (is (= [[0] [0 1]] @*calls))
    (A 2) (A 3)
    (is (= [[0] [0 1] [0 1 2] [0 1 2 3]] @*calls))
    (dispose)
    (A 4)
    (is(= [[0] [0 1] [0 1 2] [0 1 2 3]] @*calls))))

(deftest eduction-test
  (let [*calls (atom [])
        A (f/source 0)
        B (xf/eduction (filter even?) A)
        C (f/signal (* @B @B))
        D (xf/eduction (map inc) C)
        Z (f/effect [_] (swap! *calls conj @D))
        dispose (Z)]
    (is (= [1] @*calls))
    (A 1)
    (is (= [1] @*calls))
    (A 2)
    (is (= [1 5] @*calls))
    (A 3)
    (is (= [1 5] @*calls))
    (A 4)
    (is (= [1 5 17] @*calls))
    (dispose)
    (A 5)
    (A 6)
    (is (= [1 5 17] @*calls))))

(deftest collect
  (let [*calls (atom [])
        A (f/source 0)
        B (xf/collect [] (map inc) A)
        Z (f/effect [_] (swap! *calls conj @B))
        dispose (Z)]
    (is (= [[1]] @*calls))
    (A 1)
    (is (= [[1] [1 2]] @*calls))
    (A 2) (A 3)
    (is (= [[1] [1 2] [1 2 3] [1 2 3 4]] @*calls))
    (dispose)
    (A 4)
    (is (= [[1] [1 2] [1 2 3] [1 2 3 4]] @*calls))))

(deftest stateful-xform
  (let [*calls (atom [])
        A (f/source 0)
        B (xf/collect [] (drop 2) A)
        Z (f/effect [_] (swap! *calls conj @B))
        dispose (Z)]
    (is (= [[]] @*calls))
    (A 1) (A 2)
    (is (= [[] [2]] @*calls))
    (A 3) (A 4)
    (is (= [[] [2] [2 3] [2 3 4]] @*calls))
    (dispose)
    (testing "stateful xform reset on dispose"
      (Z)
      (is (= [[] [2] [2 3] [2 3 4] []] @*calls))
      (A 5) (A 6)
      (is (= [[] [2] [2 3] [2 3 4] [] [6]] @*calls)))))

(deftest sliding
  (let [*calls (atom [])
        A (f/source 0)
        B (xf/sliding 3 A)
        Z (f/effect [_] (swap! *calls conj @B))
        dispose (Z)]
    (A 1)
    (A 2)
    (A 3)
    (A 4)
    (is (= '[(0) (1 0) (2 1 0) (3 2 1) (4 3 2)] @*calls))
    (testing "state reset on dispose"
      (dispose)
      (Z)
      (is (= '[(0) (1 0) (2 1 0) (3 2 1) (4 3 2) (4)] @*calls)))))

(comment
  (t/run-tests))
