^{:clay {:hide-code true
         :hide-info-line true}}
(ns karka-and-nechasim
  (:require [clojure.data.json :as json]
            [scicloj.kindly.v4.kind :as kind]))

(def raw
  (json/read-str (slurp "karka_and_nechasim.geojson")
                 :key-fn keyword))

(def features
  (:features raw))

(def columns
  [{:label "id"     :k :ID}
   {:label "שם נכס" :k (keyword "שם נכ�")}
   {:label "סוג"    :k :סוג}
   {:label "יעוד"   :k :ייעוד}])

(defn ->row [feature]
  (let [props (:properties feature)]
    (mapv (fn [{:keys [k]}] (get props k)) columns)))

(def by-geometry
  (->> features
       (group-by :geometry)
       (map (fn [[geom fs]]
              {:geometry geom
               :rows (->> fs
                          (map ->row)
                          (sort-by first)
                          vec)}))
       vec))

(def column-labels (mapv :label columns))

(kind/reagent
 ['(fn [data labels]
     [:div {:style {:height "600px"}
            :ref (fn [el]
                   (when el
                     (let [m (-> js/L (.map el))
                           group (.featureGroup js/L)
                           cell (fn [v]
                                  (str "<td style='border:1px solid #ccc;padding:2px 6px'>"
                                       (if (nil? v) "" v) "</td>"))
                           header (fn [l]
                                    (str "<th style='border:1px solid #ccc;padding:2px 6px;background:#eee'>"
                                         l "</th>"))
                           row->html (fn [row]
                                       (str "<tr>" (apply str (map cell row)) "</tr>"))
                           table-html (fn [rows]
                                        (str "<table style='border-collapse:collapse;font-size:12px;direction:rtl'>"
                                             "<thead><tr>" (apply str (map header labels)) "</tr></thead>"
                                             "<tbody>" (apply str (map row->html rows)) "</tbody></table>"))]
                       (-> js/L .-tileLayer
                           (.provider "CartoDB.Positron")
                           (.addTo m))
                       (doseq [{:keys [geometry rows]} data]
                         (let [layer (.geoJSON js/L
                                               (clj->js geometry)
                                               (clj->js {:style {:weight 1}}))]
                           (.bindTooltip layer (table-html rows))
                           (.addLayer group layer)))
                       (.addTo group m)
                       (.fitBounds m (.getBounds group)))))}])
  by-geometry
  column-labels]
 {:html/deps [:leaflet]})
