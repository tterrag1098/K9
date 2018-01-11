(do 
  (clojure.core/use '[clojure.core])
  (require '[clojure.string :as str])
)

(let [ res (->> "%s"
                str/split-lines
                (map (fn [x] (str "  \"" (str/replace x "=" "\": \"") \")))
                (str/join ",\n")) ]
  (str "{\n" res "\n}"))