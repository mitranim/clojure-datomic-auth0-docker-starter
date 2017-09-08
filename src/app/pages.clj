(ns app.pages
  (:require
    [clojure.pprint :refer [pprint] :rename {pprint ppr}]
    [ring.util.anti-forgery :refer [anti-forgery-field]]
    [datomic.api :as d]
    [app.dat :as dat :refer [db get-user-name]]
    [app.util :as util :refer [*req* svg]]
    ))

(defn site-head [& content]
  [:head
   [:base {:href "/"}]
   [:meta {:charset "utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
   [:link {:rel "stylesheet" :href "styles/main.css"}]
   [:link {:rel "icon" :href "data:;base64,="}]
   content])

(defn format-msg [msg]
  (cond (not msg) ""
        (string? msg) msg
        :else (util/ppstr msg)))

(defn msg-pane [msg]
  (cond (not msg)         nil
        (sequential? msg) [:div.col-start-stretch.gaps-1-v (map msg-pane msg)]
        (:error msg)      [:pre.pane.--red (format-msg (:error msg))]
        (:success msg)    [:pre.pane.--green (format-msg (:success msg))]
        :else             [:pre.pane (format-msg msg)]))

(defn site-flash [] (msg-pane (:flash *req*)))

(defn site-body [nav & content]
  [:body
   [:div.page-container.gaps-1-v
    nav
    [:div.padding-1.col-start-stretch.gaps-2-v
     (site-flash)
     content]]
   [:script {:src "main.js" :async true}]])

(defn site-nav [title]
  [:div
   [:div.row-between-stretch
    [:div.row-start-stretch
     [:a.nav-link {:href "/"} (svg "home.svg") [:span "AppStarter"]]
     [:a.nav-link {:href "/posts/all"} "Posts"]
     [:a.nav-link {:href "/posts/create"} (svg "plus.svg") [:span "Create Post"]]]
    [:div.row-end-stretch
     (if-let [user (:user *req*)]
       (list [:a.nav-link {:href "/profile"} (get-user-name user)]
             [:a.nav-link {:href "/logout"} (svg "log-out.svg")])
       [:a.nav-link {:href "/login"} "Login"])]]
   [:h1.padding-1.font-large title]])

(defn common-layout [{title :title} & content]
  [:html
   (site-head [:title (or title "AppStarter")])
   (site-body (site-nav title) content)])

(defn page-404 []
  (let [title "404 Page Not Found"]
    (common-layout
      {:title title}
      [:div.col-start-center.gaps-1-v
       [:h1.font-large title]
       [:a.btn-text.busy-bg-primary {:href "/"} "Home"]])))

(defn page-500 []
  (let [title "500 Internal Server Error"]
    (common-layout
      {:title title}
      [:div.col-start-center.gaps-1-v
       [:h1.font-large title]
       [:p "Oops, something went wrong. Sorry!"]
       [:a.btn-text.busy-bg-primary {:href "/"} "Home"]])))

(defn page-401 [title]
  (common-layout
    {:title title}
    [:div.col-start-center.gaps-1-v
     [:h1.font-large "401 Unauthenticated"]
     [:div.row-center-center.gaps-1-h
      [:a.btn-text.busy-bg-primary {:href "/login"} "Login to View"]
      [:span "or"]
      [:a.btn-text.busy-bg-light {:href "/"} "Home"]]]))

(defn page-403-forgery [{uri :uri}]
  (let [title "403 Invalid Anti-Forgery Token"]
    (common-layout
      {:title title}
      [:div.col-start-center.gaps-1-v
       [:h1.font-large title]
       [:a.btn-text.busy-bg-primary {:href uri} "Refresh to Retry"]])))

(defn index []
  (common-layout
    {:title "Index Page"}
    (if-let [user (:user *req*)]
      [:div "Welcome, " (get-user-name user) "!"]
      [:div "Welcome!"])))

(defn author-time-subtitle [{created-at :created-at} author]
  [:h4.row-start-center.gaps-0x5-h
   (if-let [name (get-user-name author)]
     [:span "By " name]
     [:span.fg-gray "By anonymous"])
   (when created-at [:span.fg-gray.font-small "at " created-at])])

(defn posts-all []
  (let [posts (dat/get-all-posts @db)]
    (common-layout
      {:title "Posts"}
      (if (empty? posts)
        [:div.col-start-center.gaps-1-v
         [:h1.font-large "There are no posts yet"]
         [:a.btn-text.busy-bg-primary {:href "/posts/create"} "Create Post"]]
        [:div.gaps-1-v
         (for [post posts]
           [:a.col-start-stretch.busy-bg-light.padding-1.gaps-1-v
            {:href (str "/posts/view/" (:db/id post))}
            [:h3.font-large (:post/title post)]
            (author-time-subtitle post (:post/author post))])]))))

(defn post-view [id]
  (let [post (dat/get-post @db id)
        author (:post/author post)
        user-id (get-in *req* [:user :db/id])]
    (common-layout
      {:title (or (:post/title post) "Post")}
      (author-time-subtitle post author)
      [:article.fancy-typography (util/md-to-html (:post/body post))])))

(defn post-form [{post :post, msg :msg}]
  (let [title "Create Post"
        user (:user *req*)]
    [:html
     (site-head [:title title])
     (site-body
       (site-nav
         [:div.row-between-center.gaps-1-h
          [:span title]
          (when-not user
            [:a.btn-text.busy-bg-primary.font-regular {:href "/login"} "Login Before Submitting"])])
       [:div.gaps-2-v
        [:form.gaps-2-v {:method "post" :action "/posts/create"}
         (anti-forgery-field)
         [:div.input-container
          [:label.input-label "Title"]
          [:input.input
           {:name "post/title"
            :placeholder "My post title"
            :value (:post/title post)
            :autofocus ""}]]
         [:div.input-container
          [:label.input-label "Content"]
          [:textarea.input
           {:name "post/body"
            :placeholder "My post content"
            :rows 10}
           (:post/body post)]]
         (when msg (msg-pane msg))
         [:button.btn-text.busy-bg-primary {:type "submit"}
          [:span.row-center-center.gaps-0x5-h (svg "check.svg") [:span "Submit"]]]]])]))

(defn post-create-form [] (post-form nil))

(defn profile []
  (if-let [user (:user *req*)]
    (common-layout
      {:title "Profile"}
      [:div.col-start-stretch.gaps-1-v
       [:h3 "Welcome, user:"]
       [:pre.fancy-typography (util/ppstr user)]])
    {:status 401 :body (page-401 "Profile")}))

(defn login [req]
  [:html
   (site-head
     [:title "Sign In to AppStarter"]
     [:style "body {background-color: rgba(0, 0, 0, 0.05)}"])
   [:body
    [:script {:src "https://cdn.auth0.com/js/lock/10.18/lock.min.js"}]
    [:div#root {:style "height: 100vh"}]
    (let [config dat/auth0-config]
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
  languageDictionary: {title: 'AppStarter'},
})
auth0Lock.show()
")])]])
