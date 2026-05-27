(ns karka-and-nechasim
  (:require [clojure.data.json :as json]
            [scicloj.kindly.v4.kind :as kind]))

(def raw
  (json/read-str (slurp "karka_and_nechasim.geojson")
                 :key-fn keyword))

(def features
  (:features raw))

(def by-geometry
  (->> features
       (group-by :geometry)
       (map (fn [[geom fs]]
              {:geometry geom
               :ids (mapv (comp :ID :properties) fs)}))
       vec))

(kind/reagent
 ['(fn [data]
     [:div {:style {:height "600px"}
            :ref (fn [el]
                   (when el
                     (let [m (-> js/L (.map el))
                           group (.featureGroup js/L)]
                       (-> js/L .-tileLayer
                           (.provider "CartoDB.Positron")
                           (.addTo m))
                       (doseq [{:keys [geometry ids]} data]
                         (let [layer (.geoJSON js/L
                                               (clj->js geometry)
                                               (clj->js {:style {:weight 1}}))]
                           (.bindTooltip layer
                                         (str "IDs: " (clojure.string/join ", " ids)))
                           (.addLayer group layer)))
                       (.addTo group m)
                       (.fitBounds m (.getBounds group)))))}])
  by-geometry]
 {:html/deps [:leaflet]})
