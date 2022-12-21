(ns bb-dialog.core
  (:require [babashka.process :refer [shell]]
            [babashka.fs :refer [which]]
            [clojure.string :as str]))

(def ^:dynamic *dialog-command*
  "A var which attempts to contain the correct version of `dialog` for the system. Given that this could potentially fail,
   and can't necessarily foresee all possibilities, the var is dynamic to allow rebinding by the end user."
  (cond
    (which "dialog") "dialog"
    (which "whiptail") "whiptail"
    (which "Xdialog") "Xdialog"
    :else nil))

(defn dialog 
  "The base function wrapper for calling out to the system's version of `dialog`.
   
   Args:
   - `type`: A string containing the CLI option for the type of dialog to display (see `man dialog`)
   - `title`: A string containing the title text for the dialog
   - `body`: A string containing the body text for the dialog
   - `args`: Any additional CLI arguments will be `apply`'d to the `shell` call; this allows for adding additional CLI arguments to dialog
   
   Returns:
   A process map as per [`babashka.process`](https://github.com/babashka/process/blob/master/API.md#process-). Of useful note are the `:exit`
   and `:err` keys, which will contain the return values from the call to `dialog`."
  [type title body & args]
  (if-let [diag *dialog-command*]
    (apply shell
           {:continue true
            :err :string}
           diag "--clear" "--title" title type body 0 0
           args)
    (throw (Exception. "bb-dialog was unable to locate a working version of dialog! Please install it in the PATH."))))

(defn message
  "Calls a message dialog (`dialog --msgbox`), which simply presents some text that can be clicked past with OK or the enter key.
   The message can be interrupted also with ESC, and so the return value is a boolean that indicates whether or not the prompt
   returned a zero exit code as from OK/enter.
   
   Args:
   - `title`: The title text of the dialog
   - `body`: The body text of the dialog
   
   Returns: boolean"
  [title body]
  (-> (dialog "--msgbox" title body) :exit zero?))

(defn confirm 
  "Calls a confirmation dialog (`dialog --yesno`), and returns a boolean depending on whether the user agreed.
   
   Args:
   - `title`: The title text of the dialog
   - `body`: The body text of the dialog
   
   Returns: boolean"
  [title body]
  (-> (dialog "--yesno" title body) :exit zero?))

(defn pause
  "Calls a confirmation dialog with a timeout (`dialog --pause`). Unless interrupted by the user selecting cancel or hitting ESC,
   the dialog will automatically end with a `true` result after `timeout` seconds.
   
   Args:
   - `title`: The title text of the dialog
   - `body`: The body text of the dialog
   - `timeout`: The number of seconds the dialog should wait before automatically exiting.
   
   Returns: boolean"
  [title body timeout]
  (-> (dialog "--pause" title body timeout) :exit zero?))

(defn input 
  "Calls an `--inputbox` dialog, and returns the user input as a string.
   
   Args:
   - `title`: The title text of the dialog
   - `body`: The body text of the dialog
   
   Returns: string"
  [title body]
  (-> (dialog "--inputbox" title body)
      :err))

(defn menu 
  "Calls a `--menu` dialog, and returns the selected option as a keyword.
   
   Args:
   - `title`: The title text of the dialog
   - `body`: The body text of the dialog
   - `choices`: A map of options to their descriptions. 
   
   By default, `choices` is assumed to be a map of keywords to strings, and returns a keyword, but you can customize this behavior with 
   optional keyword arguments:
   - `:in-fn`: a function that will be applied to convert each key to a string for use by `dialog`
   - `:out-fn`: a function that will be applied to the string option selected and returned by `dialog`, to convert it back into a 
     Clojure value
   
   Returns: keyword (or result of `out-fn`)"
  [title body choices & {:keys [in-fn out-fn] :or {in-fn name out-fn keyword}}]
  (->> choices
       (mapcat (fn [[k v]] [(in-fn k) (str v)]))
       (apply dialog "--menu" title body
              (count choices))
       :err
       out-fn))

(defn checklist
  "Calls a `--checklist` dialog, and returns the selected option as a keyword.
   
   Args:
   - `title`: The title text of the dialog
   - `body`: The body text of the dialog
   - `choices`: A list of options. Each item in the list should be a vector of 3 elements: the choice value itself, a string description, 
     and a boolean indicating whether the option is toggled or not.
     By default, the values are assumed to be keywords, and the function returns a seq of keywords, but you can customize this behavior with 
     optional keyword arguments:
   - `:in-fn`: a function that will be applied to convert each key to a string for use by `dialog`
   - `:out-fn`: a function that will be applied to each string option selected and returned by `dialog`, to convert it back into a 
     Clojure value
   
   Returns: seq of keywords (or results of `out-fn`)"
  [title body choices & {:keys [in-fn out-fn] :or {in-fn name out-fn keyword}}]
  (let [as-list (mapcat (fn [[k d s]] [(in-fn k) d (if s "ON" "off")]) choices)
        result (apply dialog "--checklist" title body (count choices) as-list)]
    (map out-fn (str/split (:err result) #" "))))