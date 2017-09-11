(ns app.pages
  (:require
    [clojure.pprint :refer [pprint] :rename {pprint ppr}]
    [ring.util.anti-forgery :refer [anti-forgery-field]]
    [datomic.api :as d]
    [app.dat :as dat :refer [db get-user-name]]
    [app.util :as util :refer
     [*req* svg string-take state-uri! state-location! sanitize-input]]
    [app.i18n :refer [xln SITE_LANGS DEFAULT_LANG flag-svg]]
    ))

(defn login-uri
  ([] (login-uri (:uri *req*)))
  ([uri] (str "/login?returnUrl=" uri)))

(defn site-head [& content]
  [:div
   [:div.row-between-stretch
    [:div.row-start-stretch
     [:a.nav-link {:href "/"} (svg "home.svg") [:span (xln :SITE_NAME)]]
     [:a.nav-link {:href "/posts/all"} (xln :posts)]
     [:a.nav-link {:href "/posts/create"} (svg "plus.svg") [:span (xln :create-post)]]]
    [:div.row-end-stretch
     (if-let [user (:user *req*)]
       (list [:a.nav-link {:href "/profile"} (get-user-name user)]
             [:a.nav-link {:href "/logout" :aria-label (xln :logout)} (svg "log-out.svg")])
       [:a.nav-link {:href (login-uri)} (xln :login)])]]
   [:div.padding-1 content]])

(defn site-foot []
  [:div.margin-2-t.padding-1.row-between-center
   [:span.flex-1.padding-0x5-v (util/year-range 2017 (util/year-now)) " " (xln :SITE_NAME)]
   [:span.flex-1.row-center-center.gaps-0x5-h
    (for [lang SITE_LANGS]
      [:a.flag-svg {:href (str (:uri *req*) "?lang=" (name lang))
                    :aria-label (name lang)}
       (flag-svg lang)])]
   [:span.flex-1.row-end-center
     [:button.pg-button.padding-0x5.busy-bg-light
      {:type "button" :onclick "window.scrollTo(0, 0)"}
      (svg "arrow-up.svg")
      ]]])

(defn msg-to-str [msg]
  (cond (not msg)     ""
        (string? msg) msg
        :else         (util/ppstr msg)))

(defn msg-pane [msg]
  (cond
    (not msg)      nil
    (:error msg)   [:pre.pane.--red   (msg-to-str (:error msg))]
    (:success msg) [:pre.pane.--green (msg-to-str (:success msg))]
    :else          [:pre.pane         (msg-to-str msg)]))

(defn msg-panes [messages]
  (when-let [messages (util/flatten-messages messages)]
    [:div.col-start-stretch.gaps-1-v (map msg-pane messages)]))

(defn hidden-inputs [dict]
  (for [[key value] dict]
    [:input {:type "hidden" :name (name key) :value (str value)}]))

(defn html-head [& content]
  [:head
   [:base {:href "/"}]
   [:meta {:charset "utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
   [:link {:rel "stylesheet" :href "styles/main.css"}]
   [:link {:rel "icon" :href "data:;base64,="}]
   content])

(defn html-body [head-content & content]
  [:body.nojs
   [:script "document.body.className = 'js'"]
   [:div.stretch-to-viewport.page-container.gaps-1-v
    head-content
    [:div.flex-1.padding-1.col-start-stretch.gaps-2-v
      (when-let [msg (:msg (:state *req*))] (msg-panes msg))
      content]
    (site-foot)]
   [:script {:src "main.js" :async true}]])

(defn page-404 []
  (let [title (xln :err-404)]
    [:html
     (html-head [:title title])
     (html-body
       (site-head [:h1.font-large title])
       [:div.col-start-center.gaps-1-v
        [:h1.font-large title]
        [:a.btn-text.busy-bg-primary {:href "/"} (xln :home)]])]))

(defn page-500 []
  (let [title (xln :err-500)]
    [:html
     (html-head [:title title])
     (html-body
       (site-head [:h1.font-large title])
       [:div.col-start-center.gaps-1-v
        [:h1.font-large title]
        [:p (xln :oops)]
        [:a.btn-text.busy-bg-primary {:href "/"} (xln :home)]])]))

(defn page-401 [title]
  [:html
   (html-head [:title (or title (xln :err-401))])
   (html-body
     (site-head [:h1.font-large (or title (xln :err-401))])
     [:div.col-start-center.gaps-1-v
      [:h1.font-large (xln :err-401)]
      [:div.row-center-center.gaps-1-h
       [:a.btn-text.busy-bg-primary {:href (login-uri)} (xln :login)]
       [:span (xln :or)]
       [:a.btn-text.busy-bg-light {:href "/"} (xln :home)]]])])

(defn page-403-forgery [{uri :uri}]
  (let [title "403 Invalid Anti-Forgery Token"]
    [:html
     (html-head [:title title])
     (html-body
       (site-head [:h1.font-large title])
       [:div.col-start-center.gaps-1-v
        [:h1.font-large title]
        [:div.row-center-center.gaps-1-h
         [:a.btn-text.busy-bg-primary {:href uri} (xln :retry)]
         [:span (xln :or)]
         [:a.btn-text.busy-bg-light {:href "/"} (xln :home)]]])]))

(defn index []
  (let [title (xln :SITE_NAME)]
    [:html
     (html-head [:title title])
     (html-body
       (site-head [:h1.font-large title])
       (if-let [user (:user *req*)]
         [:div (str (xln :welcome) ", " (get-user-name user) "!")]
         [:div (str (xln :welcome) "!")]))]))

(defn post-meta-subtitle [{inst :inst} author]
  [:h4.row-start-center.gaps-0x5-h
   (if-let [name (get-user-name author)]
     [:span (xln :by) " " name]
     [:span.fg-gray (xln :by) " " (xln :anon)])
   (when inst [:span.fg-gray.font-small (xln :at) " " inst])])

(def ABRIDGED_LENGTH 120)
(def ALLOTTED_MD_LENGTH (* ABRIDGED_LENGTH 2))

(defn md-preview [md-long]
  {:pre [(string? md-long)]}
  (if (> (count md-long) ABRIDGED_LENGTH)
    (let [md-short (util/md-to-text (string-take md-long ALLOTTED_MD_LENGTH))
          text-short (string-take md-short ABRIDGED_LENGTH)]
      (list [:span text-short]
            " … "
            [:span.margin-1-l (xln :read-full) " →"]))
    [:span (util/md-to-text md-long)]))

(defn post-list [title posts]
  [:html
   (html-head [:title title])
   (html-body
     (site-head [:h1.font-large title])
     (if (empty? posts)
       [:div.col-start-center.gaps-1-v
        [:h1.font-large (xln :no-posts-yet)]
        [:a.btn-text.busy-bg-primary {:href "/posts/create"} (xln :create-post)]]
       [:div.gaps-1-v
        (for [post posts]
          [:a.col-start-stretch.busy-bg-light.padding-1.gaps-1-v
           {:href (str "/posts/view/" (:db/id post))}
           [:h3.font-large (:post/title post)]
           (post-meta-subtitle post (:post/author post))
           [:div.fancy-typography
            (md-preview (:post/body post))]])]))])

(defn posts-all []
  (post-list (xln :posts) (dat/q-posts-by dat/all-posts-q @db)))

(defn post-view [id]
  (let [post (dat/q-post @db id)]
    (if-not post
      {:status 404 :body (page-404)}
      (let [title   (:post/title post)
            author  (:post/author post)
            user-id (get-in *req* [:user :db/id])]
        [:html
         (html-head [:title title])
         (html-body
           (site-head [:h1.font-large title])
           [:div.gaps-2-v
            (post-meta-subtitle post author)
            [:article.fancy-typography (util/md-to-html (:post/body post))]])]))))

(defn post-form [{post :post}]
  (let [title (xln :create-post)
        user (:user *req*)]
    [:html
     (html-head [:title title])
     (html-body
       (site-head [:h1.font-large title])
       [:div.gaps-2-v
        (post-meta-subtitle post user)
        [:form.gaps-2-v {:method "post" :action (if user "/posts/create" "/posts/login-create")}
         (anti-forgery-field)
         (hidden-inputs {:destination (:uri *req*)})
         [:div.input-container
          [:label.input-label (xln :title)]
          [:input.input
           {:name "post/title"
            :placeholder (xln :my-post-title)
            :value (:post/title post)
            :autofocus ""}]]
         [:div.input-container.fancy-typography
          [:label.input-label (xln :body)]
          [:textarea.input
           {:name "post/body"
            :placeholder (xln :my-post-body)
            :rows 10}
           (:post/body post)]]
         [:div.row-end-start
          (if user
            [:button.btn-text.busy-bg-primary
             [:span.row-center-center.gaps-0x5-h
              (svg "check.svg")
              [:span (xln :save-post)]]]
            [:button.btn-text.busy-bg-primary
             [:span.row-center-center.gaps-0x5-h
              (svg "check.svg")
              [:span (xln :login-to-submit)]]]
            )]]])]))

(defn post-create-form []
  (post-form {:post (:post (:state *req*))}))

(defn post-login-create-submit []
  (let [params      (:params *req*)
        destination (:destination params)
        proposed    {:post/title (sanitize-input (:post/title params))
                     :post/body  (sanitize-input (:post/body params))}
        errors      (dat/post-errors proposed)
        state       {:post proposed :msg (and errors {:error errors})}
        return-uri  (state-uri! destination state)]
    {:status 303
     :headers {"Location" (if errors return-uri (login-uri return-uri))}}))

(defn post-create-submit []
  (let [params      (:params *req*)
        destination (:destination params)
        proposed    {:post/title (sanitize-input (:post/title params))
                     :post/body  (sanitize-input (:post/body params))}
        result      (dat/post-upsert proposed)]
    (if (:error result)
      (let [state {:post proposed :msg result}]
        {:status 303
         :headers (state-location! destination state)})
      (let [state {:msg {:success (xln :post-created)}}
            id    (:db/id (:success result))]
        {:status 303
         :headers (state-location! (str "/posts/view/" id) state)}))))

(defn profile []
  (let [user (:user *req*)]
    (if-not user
      {:status 401 :body (page-401 (xln :profile))}
      [:html
       (html-head [:title (xln :profile)])
       (html-body
         (site-head [:h1.font-large (xln :profile)])
         [:div.gaps-1-v
          [:h2.font-large (xln :welcome) ":"]
          [:pre.fancy-typography (util/ppstr user)]]
         )])))

(defn login [req]
  (let [config dat/auth0-config]
    [:html
     (html-head
       [:title (str (xln :SITE_NAME) ": " (xln :login))]
       [:style "body {background-color: rgba(0, 0, 0, 0.05)}"])
     [:body
      [:script {:src "https://cdn.auth0.com/js/lock/10.18/lock.min.js"}]
      [:div#root {:style "height: 100vh"}]
      [:script (str "
var auth0Lock = new Auth0Lock('" (:client-id config) "', '" (:domain config) "', {
  auth: {
    redirect: true,
    responseType: 'code',
    params: {
      scope: '" (:scope config) "',
      state: 'nonce=" (:nonce req) "&returnUrl=" (get-in req [:query-params "returnUrl"]) "',
    },
    redirectUrl: window.location.origin + '" (:callback-path config) "',
  },
  container: 'root',
  languageDictionary: {title: '" (xln :SITE_NAME) "'},
  language: '" (name (or (:lang req) DEFAULT_LANG)) "',
})
auth0Lock.show()
")]]]))
