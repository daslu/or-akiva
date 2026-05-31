^{:clay {:hide-code true
         :hide-info-line true}}
(ns layers
  (:require [clojure.data.json :as json]
            [scicloj.kindly.v4.kind :as kind]))

(def colors
  {:karka-and-helka-70      "#888888"
   :karka-and-miscellanious "#4a7ba6"
   :karka-and-nechasim      "#dda66e"
   :karka-and-shatsap       "#d62728"
   :yeud-karka              "#dda66e"
   :mivney-dat              "#9467bd"
   :herum                   "#ff7f0e"})

#_"Value sets used by layer filters"

(def Ystr-to-keep
  #{#_"שטח ציבורי פתוח"
    "בניני ציבור"
    "מוסד ציבורי"
    "מרכז אזרחי"
    "מבנים ומוסדות ציבור"
    #_"שצ\"פ אינטנסיבי"
    "שטח ספורט"
    "שטח למוסדות חינוך"
    #_"שטח ציבורי פתוח מיוחד"
    #_"שצ\"פ אקסטנסיבי ב'"
    "מוסד"
    "בית עלמין"})

(def MAVAT_NAME-to-keep
  #{"מבנים ומוסדות ציבור"
    #_"שטח ציבורי פתוח"
    "ספורט ונופש"
    #_"פארק / גן ציבורי"})

#_"Tooltip field definitions.
   A field is [label key], where key is either a property keyword or a vector
   of candidate keywords (first non-nil wins — useful for mojibake key drift
   across source files)."

(def karka-fields
  [["id"     :ID]
   ["שם נכס" (keyword "שם נכ�")]
   ["סוג"    :סוג]
   ["יעוד"   :ייעוד]])

(def building-fields
  [["סוג"    :סוג]
   ["שם נכס" [(keyword "שם נכס") (keyword "שם נכ�")]]
   ["יעוד"   :ייעוד]])

#_"Data-oriented layer configuration.

   Each layer is a map:
     :key      colour/identity key (see `colors`)
     :name     optional Hebrew display name (defaults to the key name)
     :sources  vector of source maps, each:
                 :file    geojson filename
                 :filter  optional vector of {:field <kw> :values <set>}
                          criteria, OR-combined (a feature is kept when ANY
                          criterion matches). Absent => keep all features.
     :fields   tooltip field definitions"

(def layers-config
  [{:key :yeud-karka
    :sources (mapv (fn [file]
                     {:file file
                      :filter [{:field :Ystr      :values Ystr-to-keep}
                               {:field :MAVAT_NAME :values MAVAT_NAME-to-keep}]})
                   ["yeud_karka1.geojson"
                    "yeud_karka2.geojson"
                    "yeud_karka3.geojson"
                    "yeud_karka4.geojson"])
    :fields [["גוש"  :gush_txt]
             ["חלקה" :helka_txt]
             ["יעוד" :Ystr]]}
   {:key :karka-and-helka-70
    :sources [{:file "karka_and_70.geojson"}]
    :fields karka-fields}
   {:key :karka-and-miscellanious
    :sources [{:file "karka_and_misc.geojson"}]
    :fields karka-fields}
   {:key :karka-and-nechasim
    :sources [{:file "karka_and_nechasim.geojson"}]
    :fields karka-fields}
   {:key :karka-and-shatsap
    :sources [{:file "karka_shatsap.geojson"}]
    :fields karka-fields}
   {:key :mivney-dat
    :name "מבני דת"
    :sources [{:file "Buildings.geojson"
               :filter [{:field :סוג
                         :values #{"בית כנסת"
                                   "מבנה דת"
                                   "מקווה"
                                   "משרדי מועצה דתית"}}]}]
    :fields building-fields}
   {:key :herum
    :name "חירום"
    :sources [{:file "Buildings.geojson"
               :filter [{:field :סוג
                         :values #{"חירום"
                                   "מחסן חירום"}}]}
              {:file "helka_70.geojson"
               :filter [{:field :סוג
                         :values #{"שירותי חירום"}}]}]
    :fields building-fields}])

(defn load-features [path]
  (-> (slurp path)
      (json/read-str :key-fn keyword)
      :features))

(defn feature-passes?
  "True when `criteria` is nil/empty (keep all) or ANY criterion matches the
  feature's properties."
  [criteria feature]
  (or (empty? criteria)
      (boolean
       (some (fn [{:keys [field values]}]
               (-> feature :properties field values))
             criteria))))

(defn load-source
  "Load and filter features from a single source map."
  [{:keys [file] :as source}]
  (let [criteria (:filter source)]
    (->> (load-features file)
         (filterv #(feature-passes? criteria %)))))

(defn field-value
  "Look up a tooltip field value. `k` is a keyword or a vector of candidate
  keywords (first non-nil wins)."
  [props k]
  (if (sequential? k)
    (some (fn [kk] (let [v (get props kk)] (when (some? v) v))) k)
    (get props k)))

(defn ->tooltip-html [title fields features]
  (str "<div style='max-width:600px;max-height:400px;overflow:auto'>"
       "<b>" title "</b>"
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
                                   (let [v (field-value (:properties f) k)]
                                     (if (nil? v) "" v))
                                   "</td>")))
                     "</tr>")))
       "</table></div>"))

(defn build-layer [{:keys [key sources fields] :as layer}]
  (let [layer-name (or (:name layer) (name key))
        features (mapcat load-source sources)
        groups (->> features
                    (group-by :geometry)
                    (mapv (fn [[geom fs]]
                            {:geometry geom
                             :tooltip (->tooltip-html layer-name fields fs)})))]
    {:key key
     :name layer-name
     :color (get colors key "#666666")
     :geometry-type (-> features first :geometry :type)
     :n-features (count features)
     :n-groups (count groups)
     :groups groups}))

(def layers-data
  (mapv build-layer layers-config))

#_(kind/table
   {:column-names ["layer" "geometry" "features" "unique geoms" "colour"]
    :row-vectors (mapv (juxt :name :geometry-type :n-features :n-groups :color)
                       layers-data)})

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
       (filter #(-> % :key (= :mivney-dat)))
       first
       :sources
       (map (fn [{:keys [file]}]
              [file
               (->> file
                    load-features
                    (map (comp :סוג :properties))
                    frequencies
                    (sort-by first))])))
