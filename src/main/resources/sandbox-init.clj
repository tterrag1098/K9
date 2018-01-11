(do 
  (clojure.core/use '[clojure.core])
  (require '[clojure.string :as str])
  (require '[clojure.pprint :as pp])
  (require '[clojure.math.numeric-tower :as math])
)

(let [ res (->> "%s"
                str/split-lines
                (map (fn [x] (str "  \"" (str/replace x "=" "\": \"") \")))
                (str/join ",\n")) ]
  (str "{\n" res "\n}"))