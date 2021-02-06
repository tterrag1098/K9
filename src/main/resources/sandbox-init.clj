(do 
  ;; Imports and aliases
  (clojure.core/use '[clojure.core])
  (require '[clojure.string :as str])
  (require '[clojure.pprint :as pp])
  (require '[clojure.math.numeric-tower :as math])
  
  ;; Internal use
  (defn wrap-result [res] 
    (let [dict {:res res :delete-self (.get k9.sandbox/*delete-self*)}]
      (do (.set k9.sandbox/*delete-self* false) dict))) ; unset delete-self value before we complete
  
  (defn exec [res nofn & args]
    (wrap-result (if (and (ifn? res) (not nofn)) (apply res args) res)))

  ;; Convenience functions
  (defn codeblock [s & { type :type }] (str "```" type "\n" s "\n```"))

  (defn delete-self [] (.set k9.sandbox/*delete-self* true))

  ;; Embed utilities
  (def embed-field (memfn field t d i))

  (defn embed-create [title desc & fields]
    (let [builder (com.tterrag.k9.util.EmbedCreator/builder)]
      (do
        (when title (.title       builder title))
        (when desc  (.description builder desc))
        (doseq [[t d i] (partition 3 fields)] (embed-field builder t d i))
        builder)))
  
  (defn embed-stamp 
    ([embed] (embed-stamp embed (java.time.Instant/now)))
    ([embed timestamp] (.timestamp embed timestamp) embed))

  ;; Simple embed function bindings
  (def embed-color (memfn color r g b))
  (def embed-image (memfn image url))
  (def embed-thumb (memfn thumbnail url))
  (def embed-url   (memfn url url))
  (def embed-aname (memfn authorName name))
  (def embed-aicon (memfn authorIcon url))
  (def embed-aurl  (memfn authorUrl url))
)
