(ns jogl-packager.core
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io File]
           [java.util Properties]))

(def package-folder "temp")

(defn extract-version [module]
  (let [properties (Properties.)]
    (.load properties (io/input-stream (str package-folder "/jogamp-all-platforms/jogl.artifact.properties")))
    (.getProperty properties (case module
                               :gluegen "gluegen.build.version"
                               :jogl "jogl.build.version"))))

(defn create-pom [template-file-name]
  (-> (slurp template-file-name)
      (string/replace "jogl-version" (extract-version :jogl))
      (string/replace "gluegen-rt-version" (extract-version :gluegen))
      (as-> contents
            (spit (str package-folder "/pom.xml") contents))))

(defn create-gluegen-rt-pom []
  (-> (slurp "gluegen-rt-pom.xml")
      (string/replace "jogl-version" (extract-version :jogl))
      (string/replace "gluegen-rt-version" (extract-version :gluegen))
      (as-> contents
            (spit (str package-folder "/pom.xml") contents))))

(defn package-natives [source-file-prefix target-folder]
  (doseq [[source-file-post-fix target-native-folder] [["macosx-universal"
                                                        "macosx/X86_64"]
                                                       ["macosx-universal"
                                                        "macosx/X86"]
                                                       ["linux-amd64"
                                                        "linux/X86_64"]
                                                       ["linux-i586"
                                                        "linux/X86"]
                                                       ["windows-i586"
                                                        "windows/X86"]
                                                       ["windows-amd64"
                                                        "windows/X86_64"]]]
    (let [full-target-native-folder (str target-folder "/native/" target-native-folder)]
      (shell/sh "mkdir" "-p" full-target-native-folder)
      (shell/sh "7z"
                "x"
                (str source-file-prefix "-natives-" source-file-post-fix ".jar")
                (str "-o" full-target-native-folder))
      (shell/sh "rm" "-r" (str full-target-native-folder "/META-INF")))))

(defn module-name [module]
  (case module
    :jogl "jogl-all"
    :gluegen "gluegen-rt"))

(defn package-module [module]
  (let [module-name (module-name module)
        target-folder (str package-folder "/" module-name)]

    (println "packaging " target-folder)
    (shell/sh "mkdir" target-folder)
    (shell/sh "7z" "x" (str package-folder "/jogamp-all-platforms/jar/" module-name ".jar") (str "-o" package-folder "/" module-name))
    (package-natives (str package-folder "/jogamp-all-platforms/jar/" module-name)
                     target-folder)
    (shell/sh "zip" "-r" (str module-name ".jar") "." "-i" "*" :dir target-folder)))

(defn pom-template [module]
  (case module
    :jogl "jogl-all-pom.xml"
    :gluegen "gluegen-rt-pom.xml"))

(defn install-to-local-repository [module]
  (let [module-name (module-name module)
        target-folder (str package-folder "/" module-name)]

    (println "installing " module-name)
    (create-pom (pom-template module))
    (shell/sh "mvn" "install:install-file" (str "-Dfile=" target-folder "/" module-name ".jar") (str "-DpomFile=" (str package-folder "/pom.xml")))))

(defn install-to-clojars [module]
  (let [module-name (module-name module)
        target-folder (str package-folder "/" module-name)]

    (println "installing to clojars" module-name)
    (create-pom (pom-template module))

    (shell/sh "mvn"
              "deploy:deploy-file"
              (str "-Dfile=" target-folder "/" module-name ".jar")
              (str "-DpomFile=" package-folder "/pom.xml")
              "-DrepositoryId=clojars"
              "-Durl=https://clojars.org/repo")))

(defn package []
  (when (.exists (File. package-folder))
      (shell/sh "rm" "-r" package-folder))

    (shell/sh "mkdir" package-folder)

    (println "downloading jogl")
    (shell/sh "curl"
              "http://jogamp.org/deployment/jogamp-current/archive/jogamp-all-platforms.7z"
              "-o"
              (str package-folder "/jogamp-all-platforms.7z"))

    (println "extracting jogl")
    (shell/sh "7z"
              "x"
              (str package-folder "/jogamp-all-platforms.7z")
              (str "-o" package-folder))

    (package-module :gluegen)
    (package-module :jogl))

(defn package-and-install-to-clojars []
  (package)
  (install-to-clojars :gluegen)
  (install-to-clojars :jogl))

#_(package-and-install-to-clojars)
