^{:clay {:hide-code true
         :hide-info-line true}}
(ns layers
  (:require [clojure.data.json :as json]
            [scicloj.kindly.v4.kind :as kind]))

(def colors
  {;; :karka-and-helka-70      "#888888"
   :karka-and-miscellanious "#4a7ba6"
   :karka-and-nechasim      "#9b8979"
   :yeud-karka              "#dda66e"
   :mivney-dat              "#ffa000"
   :herum                   "#ff0033"
   :sport                   "#55f724"
   :chinuch                 "#7d7ac5"
   :briut                   "#c300ce"
   :kehila-tarbut           "#48af1d"
   :revaha                  "#eb8289"
   :miklatim                "#455a64"})

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
               border
     :overrides optional vector of {:match <criteria> :color <hex> :pattern <id>}
                that restyle a subset of the layer's own features. <criteria>
                has the same OR-vector shape as a source :filter. For each
                rendered geometry the first matching override wins; non-matching
                features keep the layer colour. :color sets a solid fill;
                :pattern (id into `patterns`) fills with an SVG hatch instead
                (with :color as the fallback). Lets one layer carry a visually
                distinct sub-category (e.g. the בית עלמין parcel).
     :default-on  when truthy the layer starts visible on the map; otherwise it
                  is registered in the layer control but starts off (the user
                  toggles it on). Default off."

(def layers-config
  [{:key :yeud-karka
    :name "ייעודי קרקע"
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
             ["יעוד" :Ystr]]
    :overrides [{:match [{:field :Ystr :values #{"בית עלמין"}}]
                 :pattern "cemetery-hatch"
                 :color "#7cb342"}
                {:match [{:field :Ystr :values #{"מרכז אזרחי"}}]
                 :pattern "ezrahi"}]}
   {:key :karka-and-nechasim
    :name "קרקע ונכסים"
    :sources [{:file "karka_and_nechasim.geojson"}]
    :fields karka-fields}
   #_{:key :karka-and-helka-70
      :name "קרקע וחלקה 70"
      :sources [{:file "karka_and_70.geojson"}]
      :fields karka-fields}
   {:key :karka-and-miscellanious
    :name "קרקע ושונות"
    :sources [{:file "karka_and_misc.geojson"}]
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
    :style {:color "#d40000" :weight 1.5}
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
   {:key :revaha
    :name "רווחה"
    :sources (mapv (fn [file]
                     {:file file
                      :filter [{:field :סוג
                                :values #{"מועדונית" "רווחה"}}]})
                   ["Buildings.geojson"
                    "helka_70.geojson"
                    "Shonot.geojson"])
    :fields building-fields}
   {:key :miklatim
    :name "מקלטים"
    :sources [{:file "Miklatim.geojson"}]
    :fields building-fields}
   {:key :border
    :name "גבול העיר"
    :sources [{:file "border.geojson"}]
    :style {:fill false :weight 2 :color "#1e90ff"}
    :default-on true}])

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

(defn matching-override
  "First override whose :match matches any of the group's features, or nil."
  [overrides features]
  (some (fn [o]
          (when (some #(feature-passes? (:match o) %) features) o))
        overrides))

(defn build-layer [{:keys [key sources fields overrides] :as layer}]
  (let [layer-name (or (:name layer) (name key))
        layer-color (get colors key "#666666")
        features (mapcat load-source sources)
        groups (->> features
                    (group-by :geometry)
                    (mapv (fn [[geom fs]]
                            (let [ov (matching-override overrides fs)]
                              {:geometry geom
                               :color (or (:color ov) layer-color)
                               :pattern (:pattern ov)
                               :tooltip (when (seq fields)
                                          (->tooltip-html layer-name fields fs))}))))]
    {:key key
     :name layer-name
     :color layer-color
     :style (:style layer)
     :default-on (boolean (:default-on layer))
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
        ;; every value we match on, from both source :filters and :overrides
        layer-criteria (fn [{:keys [sources overrides]}]
                         (concat (mapcat :filter sources)
                                 (mapcat :match overrides)))
        report (vec
                (for [{:keys [key sources] :as layer} config
                      :let [files (mapv :file sources)
                            criteria (layer-criteria layer)
                            present (into {} (map (fn [field]
                                                    [field (present-set files field)])
                                                  (distinct (map :field criteria))))]
                      {:keys [field values]} criteria
                      v values]
                  {:layer key :field field :value v
                   :present? (contains? (present field) v)}))
        missing (remove :present? report)
        empty-filters (for [{:keys [key] :as layer} config
                            {:keys [field values]} (layer-criteria layer)
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
  ;; reversed: layers render/list/stack in the reverse of their config order
  (mapv build-layer (reverse layers-config)))

#_"SVG <pattern> markup by id, injected into the map renderer's <defs> and
   referenced from a layer override via :pattern \"<id>\". The cemetery hatch
   is the green/yellow diagonal crosshatch used on Israeli planning maps for
   בית עלמין."
(def patterns
  {"cemetery-hatch"
   ;; opaque yellow field with a green diagonal crosshatch (both diagonals).
   ;; 6px tile keeps yellow and green both legible on the small parcel at
   ;; city zoom (finer muddies into solid, coarser shows too few lines).
   (str "<pattern id='cemetery-hatch' patternUnits='userSpaceOnUse'"
        " width='6' height='6'>"
        "<rect width='6' height='6' fill='#e4e62a'/>"
        "<path d='M0,6 L6,0 M0,0 L6,6' stroke='#4caf50' stroke-width='0.8'/>"
        "</pattern>")
   "ezrahi"
   ;; the yeud-karka tan field with single-direction blue diagonal stripes,
   ;; for מרכז אזרחי parcels (matches the layer background, adds blue ↗ lines)
   (str "<pattern id='ezrahi' patternUnits='userSpaceOnUse'"
        " width='8' height='8'>"
        "<rect width='8' height='8' fill='#dda66e'/>"
        "<path d='M0,8 L8,0' stroke='#1f5fd8' stroke-width='2'/>"
        "</pattern>")})

#_(kind/table
   {:column-names ["layer" "geometry" "features" "unique geoms" "colour"]
    :row-vectors (mapv (juxt :name :geometry-type :n-features :n-groups :color)
                       layers-data)})

(kind/reagent
 ['(fn [data patterns]
     [:div {:style {:height "700px"}
            :ref (fn [el]
                   (when el
                     (let [m (-> js/L (.map el))
                           all (.featureGroup js/L)
                           overlays (js-obj)
                           renderer (.svg js/L)
                           ;; layer-groups in config order, to re-assert
                           ;; stacking after toggles (see overlayadd below)
                           order (atom [])]
                       ;; give the map a view up front so layer/renderer adds
                       ;; happen synchronously (their DOM containers exist
                       ;; immediately); fitBounds at the end refines it
                       (.setView m (clj->js [32.51 34.92]) 13)
                       (.addTo renderer m)
                       ;; inject the SVG <pattern> defs into the renderer's <svg>
                       (let [markup (apply str (vals (js->clj patterns)))]
                         (when (seq markup)
                           (let [doc (.parseFromString
                                      (js/DOMParser.)
                                      (str "<svg xmlns='http://www.w3.org/2000/svg'>"
                                           "<defs>" markup "</defs></svg>")
                                      "image/svg+xml")
                                 defs (.. doc -documentElement -firstChild)]
                             (.appendChild (.-_container renderer)
                                           (.importNode js/document defs true)))))
                       (-> js/L .-tileLayer
                           (.provider "CartoDB.Positron")
                           (.addTo m))
                       (doseq [{:keys [name color groups default-on] style-override :style} data]
                         (let [layer-group (.featureGroup js/L)]
                           (doseq [{:keys [geometry tooltip] gcolor :color pat :pattern} groups]
                             (let [c (or gcolor color)
                                   style (clj->js (merge {:color "#000000"
                                                          :fillColor c
                                                          :weight 0.5
                                                          :opacity 1
                                                          :fillOpacity 1}
                                                         style-override))
                                   point-style (clj->js {:radius 2
                                                         :color c
                                                         :fillColor c
                                                         :weight 1
                                                         :opacity 1
                                                         :fillOpacity 1})
                                   options (clj->js
                                            {:renderer renderer
                                             :style style
                                             :pointToLayer
                                             (fn [_ latlng]
                                               (.circleMarker js/L latlng point-style))})
                                   f (.geoJSON js/L (clj->js geometry) options)]
                               (when tooltip (.bindTooltip f tooltip))
                               ;; (re)apply the SVG pattern fill each time the
                               ;; layer is shown, so it survives starting hidden
                               ;; and toggling on/off in the control
                               (when pat
                                 (.on f "add"
                                      (fn [_]
                                        (.eachLayer f
                                                    (fn [lyr]
                                                      (when (.-_path lyr)
                                                        (.setAttribute (.-_path lyr) "fill"
                                                                       (str "url(#" pat ")"))))))))
                               (.addLayer layer-group f)
                               (.addLayer all f)))
                           (swap! order conj layer-group)
                           ;; layers start off unless they opt in (e.g. גבול העיר)
                           (when default-on (.addTo layer-group m))
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
                       ;; neutral (dark grey) checkbox tick instead of browser blue
                       (let [st (.createElement js/document "style")]
                         (set! (.-textContent st)
                               ".leaflet-control-layers-selector{accent-color:#555}")
                         (.appendChild (.-head js/document) st))
                       ;; keep stacking fixed to config order: a toggled-on layer
                       ;; would otherwise pop to the top, so re-front the visible
                       ;; layers in their configured order on every overlay add
                       (.on m "overlayadd"
                            (fn [_]
                              (doseq [lg @order]
                                (when (.hasLayer m lg)
                                  (.bringToFront lg)))))
                       (.fitBounds m (.getBounds all)))))}])
  layers-data
  patterns]
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
