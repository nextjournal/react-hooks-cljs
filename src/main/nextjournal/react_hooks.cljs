(ns nextjournal.react-hooks
  (:require ["react" :as react]
            ["use-sync-external-store/shim" :refer [useSyncExternalStore]]))

;; a type for wrapping react/useState to support reset! and swap!
(deftype WrappedState [st]
  IIndexed
  (-nth [coll i] (aget st i))
  (-nth [coll i nf] (or (aget st i) nf))
  IDeref
  (-deref [^js this] (aget st 0))
  IReset
  (-reset! [^js this new-value]
   ;; `constantly` here ensures that if we reset state to a fn,
   ;; it is stored as-is and not applied to prev value.
    ((aget st 1) (constantly new-value)))
  ISwap
  (-swap! [this f] ((aget st 1) f))
  (-swap! [this f a] ((aget st 1) #(f % a)))
  (-swap! [this f a b] ((aget st 1) #(f % a b)))
  (-swap! [this f a b xs] ((aget st 1) #(apply f % a b xs))))

(defn- as-array [x] (cond-> x (not (array? x)) to-array))

(defn use-memo
  "React hook: useMemo. Defaults to an empty `deps` array."
  ([f] (react/useMemo f #js[]))
  ([f deps] (react/useMemo f (as-array deps))))

(defn use-callback
  "React hook: useCallback. Defaults to an empty `deps` array."
  ([x] (use-callback x #js[]))
  ([x deps] (react/useCallback x (to-array deps))))

(defn- wrap-effect
  ;; utility for wrapping function to return `js/undefined` for non-functions
  [f] #(let [v (f)] (if (fn? v) v js/undefined)))

(defn use-effect
  "React hook: useEffect. Defaults to an empty `deps` array.
   Wraps `f` to return js/undefined for any non-function value."
  ([f] (react/useEffect (wrap-effect f) #js[]))
  ([f deps] (react/useEffect (wrap-effect f) (as-array deps))))

(defn use-state
  "React hook: useState. Can be used like react/useState but also behaves like an atom."
  [init]
  (WrappedState. (react/useState init)))

(deftype Ref []
  IDeref
  (-deref [^js this] (.-current this))
  IReset
  (-reset! [^js this new-value] (set! (.-current this) new-value))
  ISwap
  (-swap!
    ([o f] (reset! o (f o)))
    ([o f a] (reset! o (f o a)))
    ([o f a b] (reset! o (f o a b)))
    ([o f a b xs] (reset! o (apply f o a b xs)))))

(defn- make-ref [init]
  (doto ^js (Ref.)
    ;; React recognizes any object with a `current` property as a ref
    (.. -current (set! init))))

(defn use-ref
  "React hook: useRef. Can also be used like an atom."
  ([] (use-ref nil))
  ([init] (react/useCallback (make-ref init))))

(defn use-sync-external-store [subscribe get-snapshot]
  (useSyncExternalStore subscribe get-snapshot))

(defn use-deps
  ;; TODO - test this
  "Returns a deps array containing an integer that is incremented for each new `deps` value,
   as compared with the previous value using ="
  [deps]
  (let [!counter (use-ref 0)
        !prev-deps (use-ref deps)
        prev-deps @!prev-deps]
    (reset! !prev-deps deps)
    (when (not= deps prev-deps)
      (swap! !counter inc))
    #js[@!counter]))