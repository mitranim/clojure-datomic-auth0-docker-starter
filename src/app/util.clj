(ns app.util
  (:require
    [ring.middleware.stacktrace :refer [wrap-stacktrace]]
    [clj-stacktrace.repl :refer [pst-on]]
    [clojure.pprint :refer [pprint] :rename {pprint ppr}]
    [clojure.string :as string :refer [includes? replace-first]]
    [clojure.walk :refer [keywordize-keys]]
    [hiccup.compiler :refer [render-html]]
    [hiccup.page :refer [doctype]]
    [autoclave.html :as ahtml]
    [ring.middleware.session.store :refer [SessionStore]]
    [expiring-map.core :as em]
    )
  (:import
    [org.commonmark.parser                  Parser]
    [org.commonmark.renderer.html           HtmlRenderer]
    [org.commonmark.ext.autolink            AutolinkExtension]
    [org.commonmark.ext.heading.anchor      HeadingAnchorExtension]
    [org.commonmark.ext.gfm.strikethrough   StrikethroughExtension]
    [org.commonmark.ext.gfm.tables          TablesExtension]
    ))

(def onfocus-refresh-script "
const pageLoadTime = new Date().getTime()
let refreshXhr = null

window.onfocus = function refreshOnFocus() {
  if (refreshXhr) return

  refreshXhr = new XMLHttpRequest()

  refreshXhr.onloadend = () => {
    if (refreshXhr.responseText == 'true') {
      window.location.reload()
      return
    }
    refreshXhr = null
  }
  refreshXhr.open('GET', '/__source_changed?since=' + pageLoadTime, true)
  refreshXhr.send()
}
")

(defn add-refresh-script [body]
  (replace-first body
                 "<html><head>"
                 (str "<html><head><script>" onfocus-refresh-script "</script>")))

(defn html-content-type? [headers]
  (let [type (or (get headers "Content-Type")
                 (get headers "content-type")
                 (get headers :Content-Type)
                 (get headers :content-type))]
    (boolean (and type (includes? type "text/html")))))

(defn status-err? [status]
  (and (integer? status) (>= status 400) (<= status 500)))

(defn error-display-middleware [handler]
  (let [wrapped (wrap-stacktrace handler {:color? true})]
    (fn stacktrace-handler [request]
      (let [response (wrapped request)]
        (if (and (= (:request-method request) :get)
                 (status-err? (:status response))
                 (html-content-type? (:headers response))
                 (string? (:body response)))
          (update-in response [:body] add-refresh-script)
          response)))))

(defn files-at [dir] (filter #(.isFile %) (file-seq (clojure.java.io/file dir))))

(defn file-paths [dir] (map #(.getPath %) (files-at dir)))

(defn file-names [dir] (map #(.getName %) (files-at dir)))

(defn add-file-content [dict path] (assoc dict path (slurp path)))

(defn file-paths-to-contents [dir] (reduce add-file-content {} (file-paths dir)))

(defn file-names-to-contents [dir]
  (let [files (files-at dir)]
    (reduce (fn [dict file] (assoc dict (.getName file) (slurp file))) {} files)))

(def svg (file-names-to-contents "public/icons"))

; Fix buggy implementation. Bless Clojure, mutable namespaces, and late binding.
(ns autoclave.html)
(defn- policy-factory [p] (if (instance? PolicyFactory p) p (policy p)))
(ns app.util)

(def permissive-html-policy
  (ahtml/merge-policies
    :BLOCKS
    :FORMATTING
    :IMAGES
    :LINKS
    :STYLES
    (ahtml/policy :allow-elements ["hr"])))

(defn sanitize-output [input]
  (when (string? input)
    (string/trim (ahtml/sanitize permissive-html-policy input))))

(defn sanitize-input [input]
  (when-let [out (sanitize-output input)]
    ; Fix mangled grave accents.
    ; https://www.owasp.org/index.php/OWASP_Java_Encoder_Project#tab=Grave_Accent_Issue
    ; Need to figure out how to disable this in Autoclave/OWASP
    (string/replace out "&#96;" "`")))

(defn html-to-text [input] (string/trim (ahtml/sanitize input)))

(def md-extensions
  [(AutolinkExtension/create)
   (HeadingAnchorExtension/create)
   (AutolinkExtension/create)
   (StrikethroughExtension/create)
   (TablesExtension/create)])

(def md-parser (-> (Parser/builder) (.extensions md-extensions) .build))

(def md-renderer (-> (HtmlRenderer/builder) (.extensions md-extensions) .build))

(defn md-to-html [input]
  (when input
    (->> input (.parse md-parser) (.render md-renderer) sanitize-output)))

(defn md-to-text [input]
  (when input
    (->> input (.parse md-parser) (.render md-renderer) html-to-text)))

; No out-of-bounds exception
(defn string-take [string length] {:pre [(string? string)]}
  (if (> (count string) length) (subs string 0 length) string))

(def ^:dynamic *req* nil)

(defn add-truthy [acc [key value]] (if value (assoc acc key value) acc))

(defn trim-dict [dict] {:pre [(or (nil? dict) (map? dict))]}
  (reduce add-truthy nil dict))

(defn vacate [value] (if (empty? value) nil value))

(defn compact [value] (when (sequential? value) (filter identity value)))

(defn trim-seq [value] (vacate (compact value)))

(defn trim-args [& args] (trim-seq args))

(defn sort-by-items [coll-to-sort items-to-sort-by]
  (distinct (concat (filter (set coll-to-sort) items-to-sort-by) coll-to-sort)))

(defn assoc-if [target key value]
  (if (and target value) (assoc target key value) target))

(defn response? [value]
  (and (map? value)
       (or (integer? (:status value))
           (not (nil? (:body value))))))

(defn to-html [value] (str (:html5 doctype) (render-html value)))

(defn to-response [value] (if (response? value) value {:body value}))

(defn to-html-res [value]
  (-> value
      to-response
      (update-in [:headers] assoc "Content-Type" "text/html")
      (update-in [:body] to-html)))

(defn to-html-coded [code value] (assoc (to-html-res value) :status code))

(defn non-empty-str? [value] (and (string? value) (not= (string/trim value) "")))

(defn year-now [] (.getYear (java.time.LocalDateTime/now)))

(defn year-range [year-start year-now]
  {:pre [(integer? year-start) (integer? year-now)]}
  (if (> year-now year-start)
    (str year-start "—" year-now)
    year-start))

(defn ppstr [value] (with-out-str (ppr value)))

(defn eprn [& args] (binding [*out* *err*] (apply prn args)))

(defn eprintln [& args] (binding [*out* *err*] (apply println args)))

; true = with color
(defn prn-err [err] (pst-on *err* true err))

(defn flatten-messages [msg]
  (cond
    (sequential? msg)
    (trim-seq (flatten (map flatten-messages msg)))

    (sequential? (:error msg))
    (for [submsg (flatten-messages (:error msg))] {:error submsg})

    (sequential? (:success msg))
    (for [submsg (flatten-messages (:success msg))] {:success submsg})

    msg (list msg)

    :else nil))

(defn wrap-with-dynamic-req [handler]
  (fn with-dynamic-req [req] (binding [*req* req] (handler req))))

(defn keywordize-params [req] (update-in req [:params] keywordize-keys))

(defn wrap-keywordize-params [handler] (comp handler keywordize-params))

(defn wrap-handle-500 [handler page-500]
  {:pre [(fn? page-500)]}
  (fn handle-500 [request]
    (try (handler request)
      (catch Exception err
        (prn-err err)
        (to-html-coded 500 (page-500))))))

(deftype ExpiringSessionStore [expiring-dict]
  SessionStore
  (read-session [_ key]
    (get expiring-dict key))
  (write-session [_ key data]
    (let [key (str (or key (java.util.UUID/randomUUID)))]
      (em/assoc! expiring-dict key data)
      key))
  (delete-session [_ key]
    (em/dissoc! expiring-dict key)
    nil))

(defn expiring-session-store [& opts]
  (new ExpiringSessionStore (apply em/expiring-map opts)))

(defonce exp-store (em/expiring-map 10 {:time-unit :minutes
                                        :expiration-policy :access}))

(defn exp-push! [value]
  (let [uuid (str (java.util.UUID/randomUUID))]
    (em/assoc! exp-store uuid value)
    uuid))

(defn exp-pop! [key]
  (when (contains? exp-store key)
    (let [value (get exp-store key)]
      (em/dissoc! exp-store key)
      value)))

(defn state-uri! [uri state] {:pre [(string? uri)]}
  (str uri "?state-id=" (exp-push! state)))

(defn state-location! [uri state] {"Location" (state-uri! uri state)})

(defn restore-state [request]
  (let [state-id (get-in request [:query-params "state-id"])
        state (get exp-store state-id)]
    (assoc request :state state)))

(defn wrap-restore-state [handler] (comp handler restore-state))
