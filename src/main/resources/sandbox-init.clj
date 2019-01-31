(do 
  ;; Imports and aliases
  (clojure.core/use '[clojure.core])
  (require '[clojure.string :as str])
  (require '[clojure.pprint :as pp])
  (require '[clojure.math.numeric-tower :as math])
  
  ;; Convenience functions
  (defn codeblock [s & { type :type }] (str "```" type "\n" s "\n```"))

  (defn delete-self [] (alter-var-root #'k9.sandbox/*delete-self* (constantly true)))

  ;; Embed utilities
  (defn embed-create [title desc & fields]
    (let [builder (com.tterrag.k9.util.EmbedCreator/builder)]
      (do
        (when title (.title       builder title))
        (when desc  (.description builder desc))
        (doseq [[t d i] (partition 3 fields)] (.field builder (com.tterrag.k9.util.EmbedCreator$EmbedField. t d i)))
        builder)))
  
  (defn embed-stamp [embed] (do (.withTimestamp embed (java.time.Instant/now) ) embed))

  ;; Simple embed function bindings
  (def embed-field (memfn appendField title desc inline))
  (def embed-color (memfn withColor r g b))
  (def embed-image (memfn withImage url))
  (def embed-thumb (memfn withThumbnail url))
  (def embed-url   (memfn withUrl url))
  (def embed-ftext (memfn withFooterText text))
  (def embed-ficon (memfn withFooterIcon url))
  (def embed-aname (memfn withAuthorName name))
  (def embed-aicon (memfn withAuthorIcon url))
  (def embed-aurl  (memfn withAuthorUrl url))
  (def embed-build (memfn build))
)
