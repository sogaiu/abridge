(ns script
  (:require
    [clojure.data.json :as cdj]
    [rewrite-clj.zip :as rz])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn position-spans
  [zloc]
  (loop [zloc zloc
         results []]
    (if (rz/end? zloc)
      results
      (let [sexpr (rz/sexpr zloc)]
        (recur (rz/next zloc)
               (if (string? sexpr)
                 (conj results (rz/position-span zloc))
                 results))))))

(defn -main [& args]
  (let [;; XXX: expects json rpc string on stdin, e.g.
        ;;        {"jsonrpc": "2.0",
        ;;         "method": "abridge-strings",
        ;;         "params": ["(defn my-fn [a] (+ a 1))"],
        ;;         "id": 1}
        {:keys [method params id]} (-> (slurp *in*)
                                       (cdj/read-str :key-fn keyword))
        [text] params
        spans (->> (rz/of-string text
                                 {:track-position? true})
                   position-spans
                   (keep (fn [[[start-row _]
                               [end-row _]]]
                           (when (> (- end-row start-row) 0)
                             [start-row end-row])))
                   vec)]
    ;; XXX: check for errors?
    (-> (assoc {"jsonrpc" "2.0", "id" id}
                "result" [spans])
        cdj/write-str
        println)))

(comment

  (require
   '[rewrite-clj.zip :as rz]
   :reload-all)

  (def source-str
    (slurp "../clojure/src/clj/clojure/main.clj"))

  (def root-zloc
    (rz/of-string source-str
                  {:track-position? true}))

  (def strings
    (loop [zloc root-zloc
           results []]
      (if (rz/end? zloc)
        results
        (let [sexpr (rz/sexpr zloc)]
          (recur (rz/next zloc)
                 (if (string? sexpr)
                   (conj results zloc)
                   results))))))

  (def pos-spans
    (loop [zloc root-zloc
           results []]
      (if (rz/end? zloc)
        results
        (let [sexpr (rz/sexpr zloc)]
          (recur (rz/next zloc)
                 (if (string? sexpr)
                   (conj results (rz/position-span zloc))
                   results))))))

  (def at-least-two-lines
    (filter (fn [[[start-row _]
                  [end-row _]]]
              (not= start-row end-row))
            pos-spans))

  (def row-pairs-for-at-least-two-lines
    (->> pos-spans
         (keep (fn [[[start-row _]
                     [end-row _]]]
                 (when (> (- end-row start-row) 0)
                   [start-row end-row])))
         vec))

  )
