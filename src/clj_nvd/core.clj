(ns clj-nvd.core
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as deps.reader]
            [jsonista.core :as json]
            [nvd.task.check]
            [nvd.task.purge-database]
            [nvd.task.update-database]))

(defn make-classpath [aliases]
  (let [deps-map (deps.reader/read-deps (deps.reader/default-deps))
        args-map (deps/combine-aliases deps-map aliases)
        lib-map  (deps/resolve-deps deps-map args-map)]
    (mapcat :paths (vals lib-map))))

(defn parse-aliases [alias-str]
  (map keyword (string/split alias-str #":")))

(def cli-options
  [["-A" nil "Colon-separated list of deps.edn aliases"
    :id :aliases
    :required "ALIASES"
    :parse-fn parse-aliases]
   ["-c" "--config-file FILE" "Config file location"
    :id :config-file
    :required "CONFIG"
    :default "clj-nvd.edn"]])

(defn run-nvd [options command args]
  (let [temp-file (java.io.File/createTempFile "clj-nvd" ".json")
        path (.getAbsolutePath temp-file)
        classpath (make-classpath (get options :aliases))
        opts {:nvd       (get options :config-file)
              :classpath classpath
              :cmd-args  args}]
    (spit path (json/write-value-as-string opts))
    (case command
      "check" (nvd.task.check/-main path)
      "purge" (nvd.task.purge-database/-main path)
      "update" (nvd.task.update-database/-main path)
      (do
        (.println *err* (str "No such command: " command))
        (System/exit 1)))))

(defn -main [& args]
  (let [{:keys [options arguments errors]} (cli/parse-opts args cli-options)]
    (cond
      errors
      (do
        (.println *err* (string/join \newline errors))
        (System/exit 1))
      :else
      (run-nvd (:options options) (first arguments) (rest arguments)))))
