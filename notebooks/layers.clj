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
   :mivney-dat              "#ffa000"
   :herum                   "#ff0033"
   :sport                   "#2ca02c"
   :chinuch                 "#9467bd"
   :briut                   "#17becf"
   :kehila-tarbut           "#8c564b"})

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

#_"Every tooltip starts with an id column. :ID is present in most files;
   the yeud files only carry :OBJECTID, so fall back to it."
(def id-field ["id" [:ID :OBJECTID]])

(def karka-fields
  [id-field
   ["שם נכס" (keyword "שם נכ�")]
   ["סוג"    :סוג]
   ["יעוד"   :ייעוד]])

(def building-fields
  [id-field
   ["סוג"    :סוג]
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
     :fields   tooltip field definitions (omit => no tooltip)
     :style    optional Leaflet path-style overrides merged over the
               defaults, e.g. {:fill false :weight 2} for an outline-only
               border"

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
    :fields [id-field
             ["גוש"  :gush_txt]
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
    :fields building-fields}
   {:key :sport
    :name "ספורט"
    :sources [{:file "Buildings.geojson"
               :filter [{:field :סוג
                         :values #{"אולמות ספורט"}}]}]
    :fields building-fields}
   {:key :chinuch
    :name "חינוך"
    :sources [{:file "Buildings.geojson"
               :filter [{:field :סוג
                         :values #{"בית ספר"
                                   "גני ילדים"
                                   "מבנה חינוך כללי"
                                   "מעונות יום"}}]}]
    :fields building-fields}
   {:key :briut
    :name "בריאות"
    :sources [{:file "Buildings.geojson"
               :filter [{:field :סוג
                         :values #{"בריאות"
                                   "טיפת חלב"}}]}]
    :fields building-fields}
   {:key :kehila-tarbut
    :name "קהילה ותרבות"
    :sources [{:file "Buildings.geojson"
               :filter [{:field :סוג
                         :values #{"קהילה"
                                   "תרבות"
                                   "תרבות וקהילה"}}]}]
    :fields building-fields}
   {:key :border
    :name "גבול העיר"
    :sources [{:file "border.geojson"}]
    :style {:fill false :weight 2 :color "#1e90ff"}}])

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
       ;; header row: one column per property
       "<tr>"
       (apply str
              (for [[label _] fields]
                (str "<th style='border:1px solid #ccc;padding:2px 6px;"
                     "background:#eee;text-align:start;white-space:nowrap'>"
                     label "</th>")))
       "</tr>"
       ;; one row per case (feature)
       (apply str
              (for [f features]
                (str "<tr>"
                     (apply str
                            (for [[_ k] fields]
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
                             :tooltip (when (seq fields)
                                        (->tooltip-html layer-name fields fs))})))]
    {:key key
     :name layer-name
     :color (get colors key "#666666")
     :style (:style layer)
     :geometry-type (-> features first :geometry :type)
     :n-features (count features)
     :n-groups (count groups)
     :groups groups}))

#_"Sensibility check on layer filters: every value we filter on must actually
   appear, under the given field, in at least one of that layer's source
   files (catches typos), and no filter may have an empty value set (catches
   a filter that would keep nothing). The check is per-layer-union, since a
   layer may apply one value set across several files where each value only
   occurs in some of them (e.g. :yeud-karka)."

(defn check-filter-values!
  "Validate the filter values in `config` against the actual data.
  Throws ex-info listing any value absent from its layer's files, or any
  empty value set. Returns the (truthy) report seq when everything is fine."
  [config]
  (let [load-once (memoize load-features)
        present-set (fn [files field]
                      (->> files
                           (mapcat load-once)
                           (keep #(get-in % [:properties field]))
                           set))
        report (vec
                (for [{:keys [key sources]} config
                      :let [files (mapv :file sources)
                            criteria (mapcat :filter sources)
                            present (into {} (map (fn [field]
                                                    [field (present-set files field)])
                                                  (distinct (map :field criteria))))]
                      {:keys [field values]} criteria
                      v values]
                  {:layer key :field field :value v
                   :present? (contains? (present field) v)}))
        missing (remove :present? report)
        empty-filters (for [{:keys [key sources]} config
                            {:keys [field values]} (mapcat :filter sources)
                            :when (empty? values)]
                        {:layer key :field field})]
    (when (or (seq missing) (seq empty-filters))
      (throw (ex-info "Layer filter sensibility check failed: filter values missing from data (typo?) or empty value set."
                      {:missing missing
                       :empty-filters (vec empty-filters)})))
    report))

(def filter-value-report
  (check-filter-values! layers-config))

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
                       (doseq [{:keys [name color groups] style-override :style} data]
                         (let [layer-group (.featureGroup js/L)
                               style (clj->js (merge {:color "#000000"
                                                      :fillColor color
                                                      :weight 0.5
                                                      :opacity 1
                                                      :fillOpacity 1}
                                                     style-override))
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
                               (when tooltip (.bindTooltip f tooltip))
                               (.addLayer layer-group f)
                               (.addLayer all f)))
                           (.addTo layer-group m)
                           ;; label = colour swatch + name, so the layer
                           ;; control doubles as a legend
                           (let [fill? (not (false? (:fill style-override)))
                                 stroke (or (:color style-override) "#000000")
                                 swatch (str "<span style='display:inline-block;"
                                             "width:12px;height:12px;vertical-align:middle;"
                                             "margin-inline-end:6px;border:1px solid " stroke ";"
                                             "background:" (if fill? color "transparent")
                                             "'></span>")]
                             (aset overlays (str swatch name) layer-group))))
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
