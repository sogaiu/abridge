(ns abridge.core
  (:require
   [abridge.jsonrpc :as aj]
   [abridge.vscode :as av]
   ["child_process" :as ncp]))

(defn abridge-path
  []
  ;; XXX: modify if publisher / name changes in package.json
  (str (av/extension-path "undefined_publisher.vscode-abridge")
     "/bin/abridge"))

(defn abridge-strings
  [resolve reject text]
  (av/info-message "invoking helper...")
  (let [p (.spawn ncp (abridge-path) #js [] #js {})
        stdout (.-stdout p)
        stdin (.-stdin p)]
    ;; receiving response from helper
    (.on stdout "data"
      (fn [data]
        ;; XXX: handle error case
        (let [[ranges]
              (get (aj/from-str data)
                   "result")]
          ;; XXX: when no ranges returned, inform user?
          (if ranges
            ;; row, col values need to be one less for vscode api
            (resolve
             (-> (map (fn [[start-row end-row]]
                        (av/folding-range (dec start-row) (dec end-row)))
                      ranges)
                 clj->js))
            (reject nil)))))
    (.on stdout "end"
      (fn []
        (println "finished!")))
    (.on p "close"
      (fn [code]
        (println (str "exit code: " code))))
    (.on p "error"
      (fn [err]
        (println (str "err: " err))
        (reject nil)))
    ;; sending to helper
    (.end stdin
      (aj/to-str "abridge-strings"
        ;; buffer text
        [text]))))

(defn activate
  [context]
  (.log js/console "activate called")
  (let [disposable
        (av/register-folding-range-provider
          #js {"scheme" "file"
               "language" "clojure"}
          #js {"provideFoldingRanges"
               (fn [^TextDocument doc ctxt tok]
                 (let [text (.getText doc)]
                   (js/Promise. (fn [resolve reject]
                                  (abridge-strings resolve reject text)))))})]
    (av/push-subscription! context disposable)))

(defn deactivate
  [])
