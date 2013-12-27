(ns jogl-packager.core
  (:require [clojure.java.shell :as shell])
  (:import [java.io File]))

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

(defn package-module [package-folder module-name]
  (let [target-folder (str package-folder "/" module-name)]

    (println "packaging " target-folder)
    (shell/sh "mkdir" target-folder)
    (shell/sh "7z" "x" (str package-folder "/jogamp-all-platforms/jar/" module-name ".jar") (str "-o" package-folder "/" module-name))
    (package-natives (str package-folder "/jogamp-all-platforms/jar/" module-name)
                     target-folder)
    (shell/sh "zip" "-r" (str module-name ".jar") "." "-i" "*" :dir target-folder)

    (println "installing " module-name)
    (shell/sh "mvn" "install:install-file" (str "-Dfile=" target-folder "/" module-name ".jar") (str "-DpomFile=" module-name "-pom.xml"))))


(defn package []
  (let [package-folder "temp"]

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

    (package-module package-folder "gluegen-rt")
    (package-module package-folder "jogl-all")))
