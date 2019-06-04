(ns abridge.core
  (:require [abridge.atom :as aa]
            [abridge.dispose :as ad]
            [abridge.jsonrpc :as aj]))

(def cp
  (js/require "child_process"))

(def path
  (js/require "path"))

(def abridge-path
  (let [pkg-dir-path (->> (aa/path-for-pkg "abridge")
                          (.dirname path)
                          (.dirname path))]
    (str pkg-dir-path "/bin/abridge")))

(defn abridge-strings
  []
  (aa/info-message "invoking helper...")
  (let [p (.spawn cp abridge-path #js [] #js {})
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
          (when ranges
            (let [editor (aa/current-editor)]
              ;; row, col values need to be one less for atom api
              (doseq [[start-row end-row] ranges]
                (.foldBufferRowRange editor
                                     (dec start-row) (dec end-row))))))))
    (.on stdout "end"
      (fn []
        (println "finished!")))
    (.on p "close"
      (fn [code]
        (println (str "exit code: " code))))
    (.on p "error"
      (fn [err]
        (println (str "err: " err))))
    ;; sending to helper
    (.end stdin
      (aj/to-str "abridge-strings"
        ;; buffer text
        [(aa/current-buffer)]))))

(defn activate
  [s]
  (ad/reset-disposables!)
  ;;
  (ad/command-for "abridge-strings" abridge-strings))

(defn deactivate
  [s]
  (ad/dispose-disposables!))

(defn before
  [done]
  (deactivate nil)
  (done)
  (activate nil)
  (aa/info-message "Reloaded Abridge"))
