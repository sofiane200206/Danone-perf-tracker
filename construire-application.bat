@echo off
REM ---------------------------------------------------------------------------
REM Construit l'application prete a distribuer.
REM
REM Produit deux livrables dans target\ :
REM   1. PerformanceTracker-1.0-SNAPSHOT-autonome.jar  -> demande Java 17+
REM   2. target\application\PerformanceTracker\        -> ne demande rien
REM
REM Le second est celui a copier sur les postes de l'usine : il embarque son
REM propre Java, il suffit de copier le dossier et de lancer l'executable.
REM ---------------------------------------------------------------------------

setlocal

echo.
echo [1/3] Compilation et tests...
call mvn -B clean package
if errorlevel 1 (
    echo.
    echo ECHEC : la compilation ou les tests ont echoue. Rien n'a ete produit.
    exit /b 1
)

echo.
echo [2/3] Preparation du jar autonome...
if exist target\livrable rmdir /s /q target\livrable
mkdir target\livrable
copy /y target\PerformanceTracker-1.0-SNAPSHOT-autonome.jar target\livrable\ >nul
if errorlevel 1 (
    echo ECHEC : jar autonome introuvable.
    exit /b 1
)

echo.
echo [3/3] Construction de l'application autonome (avec Java embarque)...
if exist target\application rmdir /s /q target\application
jpackage ^
    --type app-image ^
    --name PerformanceTracker ^
    --app-version 1.0 ^
    --vendor "Performance Tracker" ^
    --description "Suivi de performance de production industrielle" ^
    --input target\livrable ^
    --main-jar PerformanceTracker-1.0-SNAPSHOT-autonome.jar ^
    --main-class com.sofiane.performance.Lanceur ^
    --dest target\application
if errorlevel 1 (
    echo.
    echo ECHEC : jpackage a echoue. Verifiez que le JDK 17+ est dans le PATH.
    exit /b 1
)

echo.
echo ===========================================================================
echo  Termine.
echo.
echo  A copier sur les postes :  target\application\PerformanceTracker\
echo  Executable a lancer     :  PerformanceTracker.exe
echo.
echo  La base et les sauvegardes se creent a cote de l'executable au premier
echo  lancement. Pensez a sauvegarder ce dossier regulierement.
echo ===========================================================================
echo.

endlocal
