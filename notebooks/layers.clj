^{:clay {:hide-code true
         :hide-info-line true}}
(ns layers
  (:require [clojure.data.json :as json]
            [scicloj.kindly.v4.kind :as kind]))

;; --- Configurable colours ------------------------------------------------
;; Edit these to taste. Keys correspond to :key in `layers-config` below.
(def colors
  {:karka-and-helka-70      "#888888"
   :karka-and-miscellanious "#ff7f00"
   :karka-and-nechasim      "#1f77b4"
   :karka-and-shatsap       "#d62728"
   :yeud-karka              "#dda66e"})




;; --- Layer configuration -------------------------------------------------
;; Order = stacking order: first is bottom, last is top. The unified
;; `yeud-karka` layer is last so it sits on top of all others.
;;
;; `:fields` is a vector of `[label property-key]` pairs — only these
;; property values are passed to the browser. Everything else (URLs,
;; auxiliary labelling fields, etc.) is dropped server-side.
(def karka-fields
  [["id"     :ID]
   ["שם נכס" (keyword "שם נכ�")]
   ["סוג"    :סוג]
   ["יעוד"   :ייעוד]])

(def layers-config
  [{:key :yeud-karka
    :files ["yeud_karka1.geojson"
            "yeud_karka2.geojson"
            "yeud_karka3.geojson"
            "yeud_karka4.geojson"]
    :fields [["גוש"  :gush_txt]
             ["חלקה" :helka_txt]
             ["יעוד" :Ystr]]}
   {:key :karka-and-helka-70
    :files ["karka_and_70.geojson"]
    :fields karka-fields}
   {:key :karka-and-miscellanious
    :files ["karka_and_misc.geojson"]
    :fields karka-fields}
   {:key :karka-and-nechasim
    :files ["karka_and_nechasim.geojson"]
    :fields karka-fields}
   {:key :karka-and-shatsap
    :files ["karka_shatsap.geojson"]
    :fields karka-fields}])

;; --- Data loading + dedupe -----------------------------------------------
(defn load-features [path]
  (-> (slurp path)
      (json/read-str :key-fn keyword)
      :features))






(defn ->tooltip-html [layer-key fields features]
  (str "<div style='max-width:600px;max-height:400px;overflow:auto'>"
       "<b>" (name layer-key) "</b>"
       "<table style='border-collapse:collapse;font-size:11px;margin-top:4px'>"
       (apply str
              (for [[label k] fields]
                (str "<tr>"
                     "<th style='border:1px solid #ccc;padding:2px 6px;"
                     "background:#eee;text-align:start;white-space:nowrap'>"
                     label "</th>"
                     (apply str
                            (for [f features]
                              (str "<td style='border:1px solid #ccc;padding:2px 6px'>"
                                   (let [v (get-in f [:properties k])]
                                     (if (nil? v) "" v))
                                   "</td>")))
                     "</tr>")))
       "</table></div>"))




(def Ystr-to-keep
  #{
    ;; "שטח ציבורי פתוח"
    "בניני ציבור"
    "מוסד ציבורי"
    "מרכז אזרחי"
    "מבנים ומוסדות ציבור"
    ;; "שצ\"פ אינטנסיבי"
    "שטח ספורט"
    "שטח למוסדות חינוך"
    ;; "שטח ציבורי פתוח מיוחד"
    ;; "שצ\"פ אקסטנסיבי ב'"
    "מוסד"
    "בית עלמין"})

(def MAVAT_NAME-to-keep
  #{"מבנים ומוסדות ציבור"
    ;; "שטח ציבורי פתוח"
    "ספורט ונופש"
    ;; "פארק / גן ציבורי"
    })


(defn build-layer [{:keys [key files fields]}]
  (let [raw-features (mapcat load-features files)
        features (if (= key :yeud-karka)
                   (->> raw-features
                        (filter (fn [{:keys [properties]}]
                                  (or (-> properties
                                          :Ystr
                                          (Ystr-to-keep))
                                      (-> properties
                                          :MAVAT_NAME
                                          (MAVAT_NAME-to-keep))))))
                   raw-features)
        groups (->> features
                    (group-by :geometry)
                    (mapv (fn [[geom fs]]
                            {:geometry geom
                             :tooltip (->tooltip-html key fields fs)})))]
    {:key key
     :name (name key)
     :color (get colors key "#666666")
     :geometry-type (-> features first :geometry :type)
     :n-features (count features)
     :n-groups (count groups)
     :groups groups}))

(def layers-data
  (mapv build-layer layers-config))

;; Summary
(kind/table
 {:column-names ["layer" "geometry" "features" "unique geoms" "colour"]
  :row-vectors (mapv (juxt :name :geometry-type :n-features :n-groups :color)
                     layers-data)})

;; --- Map ----------------------------------------------------------------
(kind/reagent
 ['(fn [data]
     [:div {:style {:height "700px"}
            :ref (fn [el]
                   (when el
                     (let [m (-> js/L (.map el))
                           all (.featureGroup js/L)
                           overlays (js-obj)]
                       (-> js/L .-tileLayer
                           (.provider "CartoDB.Positron")
                           (.addTo m))
                       (doseq [{:keys [name color groups]} data]
                         (let [layer-group (.featureGroup js/L)
                               style (clj->js {:color color
                                               :fillColor color
                                               :weight 1
                                               :opacity 1
                                               :fillOpacity 1})
                               point-style (clj->js {:radius 5
                                                     :color color
                                                     :fillColor color
                                                     :weight 1
                                                     :opacity 1
                                                     :fillOpacity 1})
                               options (clj->js
                                        {:style style
                                         :pointToLayer
                                         (fn [_ latlng]
                                           (.circleMarker js/L latlng point-style))})]
                           (doseq [{:keys [geometry tooltip]} groups]
                             (let [f (.geoJSON js/L (clj->js geometry) options)]
                               (.bindTooltip f tooltip)
                               (.addLayer layer-group f)
                               (.addLayer all f)))
                           (.addTo layer-group m)
                           (aset overlays name layer-group)))
                       (-> js/L .-control
                           (.layers nil overlays (clj->js {:collapsed false}))
                           (.addTo m))
                       (.fitBounds m (.getBounds all)))))}])
  layers-data]
 {:html/deps [:leaflet]})



#_(->> layers-config
       (filter #(-> % :key (= :yeud-karka)))
       first
       :files
       (map (fn [f]
              (->> f
                   load-features
                   (map :properties)
                   (map (fn [p]
                          (:MAVAT_NAME p)))
                   frequencies
                   (sort-by first)))))





