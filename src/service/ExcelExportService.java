package service;

import model.*;
import model.MatierePremiereModel.SortieIdeale;
import model.ProductionModel.SortieReelle;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import dao.ProductionDAO;
import service.MatierePremiereService;

public class ExcelExportService {
    private static final Logger LOGGER = Logger.getLogger(ExcelExportService.class.getName());

    private final ProductionService productionService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public ExcelExportService(ProductionService productionService) {
        this.productionService = productionService;
    }

    /**
     * Exporte toutes les productions d'une matière première vers un fichier Excel
     */
    public void exporterProductions(MatierePremiereModel matiere, String cheminFichier) throws ServiceException {
        if (matiere == null) {
            throw new ServiceException("Aucune matière première sélectionnée pour l'export");
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            LOGGER.info("Début de l'export pour la matière: " + matiere.getNom());

            // Créer les feuilles
            Sheet sheetResume = workbook.createSheet("Résumé Matière");
            Sheet sheetProductions = workbook.createSheet("Productions Détaillées");
            Sheet sheetStatistiques = workbook.createSheet("Statistiques");

            // Remplir la feuille résumé
            creerFeuilleResume(sheetResume, matiere, workbook);

            // Remplir la feuille des productions
            creerFeuilleProductions(sheetProductions, matiere, workbook);

            // Remplir la feuille des statistiques
            creerFeuilleStatistiques(sheetStatistiques, matiere, workbook);

            // Sauvegarder le fichier
            try (FileOutputStream fileOut = new FileOutputStream(cheminFichier)) {
                workbook.write(fileOut);
                LOGGER.info("Export terminé: " + cheminFichier);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export Excel", e);
            throw new ServiceException("Erreur lors de la création du fichier Excel: " + e.getMessage());
        }
    }

    private void creerFeuilleResume(Sheet sheet, MatierePremiereModel matiere, Workbook workbook) {
        // Créer les styles
        CellStyle titleStyle = creerStyleTitre(workbook);
        CellStyle headerStyle = creerStyleEntete(workbook);
        CellStyle dataStyle = creerStyleDonnee(workbook);

        int rowNum = 0;

        // Titre principal
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("RAPPORT DE PRODUCTION - " + matiere.getNom().toUpperCase());
        titleCell.setCellStyle(titleStyle);

        // Date de génération
        rowNum++;
        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Date de génération:");
        dateRow.createCell(1).setCellValue(LocalDate.now().format(dateFormatter));

        // Informations sur la matière première
        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("INFORMATIONS MATIÈRE PREMIÈRE");
        headerRow.getCell(0).setCellStyle(headerStyle);

        Row nomRow = sheet.createRow(rowNum++);
        nomRow.createCell(0).setCellValue("Nom:");
        nomRow.createCell(1).setCellValue(matiere.getNom());

        Row entreeRow = sheet.createRow(rowNum++);
        entreeRow.createCell(0).setCellValue("Quantité entrée idéale:");
        entreeRow.createCell(1).setCellValue(matiere.getQuantiteEntreeIdeale() + " kg");

        // Sorties idéales
        rowNum++;
        Row sortiesHeaderRow = sheet.createRow(rowNum++);
        sortiesHeaderRow.createCell(0).setCellValue("SORTIES IDÉALES");
        sortiesHeaderRow.getCell(0).setCellStyle(headerStyle);

        if (matiere.getSortiesIdeales() != null) {
            for (SortieIdeale sortie : matiere.getSortiesIdeales()) {
                Row sortieRow = sheet.createRow(rowNum++);
                sortieRow.createCell(0).setCellValue(sortie.getNomSortie() + ":");
                sortieRow.createCell(1).setCellValue(sortie.getQuantiteIdeale() + " kg");
            }
        }

        // Redimensionner les colonnes
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    public void exporterToutesProductions(LocalDate dateDebut, LocalDate dateFin, String cheminFichier) throws ServiceException {
        if (dateDebut == null || dateFin == null) {
            throw new ServiceException("Les dates de début et de fin sont obligatoires");
        }

        if (dateDebut.isAfter(dateFin)) {
            throw new ServiceException("La date de début doit être antérieure à la date de fin");
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            LOGGER.info(String.format("Début export toutes productions du %s au %s", dateDebut, dateFin));

            // ✅ CORRECTION : Récupérer TOUTES les productions directement depuis le DAO
            ProductionDAO productionDAO = new ProductionDAO();
            List<ProductionModel> toutesProductions = productionDAO.listerToutesPeriode(dateDebut, dateFin);

            // Filtrer les productions valides dans la période
            List<ProductionModel> productionsPeriode = toutesProductions.stream()
                    .filter(p -> p.getDateProduction() != null)
                    .filter(p -> !p.getDateProduction().isBefore(dateDebut) &&
                            !p.getDateProduction().isAfter(dateFin))
                    .filter(ProductionModel::isComptabilisable)
                    .sorted((p1, p2) -> {
                        // Trier par date puis par heure
                        int dateCompare = p1.getDateProduction().compareTo(p2.getDateProduction());
                        if (dateCompare != 0) return dateCompare;

                        if (p1.getHeureProduction() != null && p2.getHeureProduction() != null) {
                            return p1.getHeureProduction().compareTo(p2.getHeureProduction());
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            if (productionsPeriode.isEmpty()) {
                throw new ServiceException("Aucune production valide trouvée dans cette période");
            }

            // Créer une seule feuille avec toutes les données brutes
            Sheet sheet = workbook.createSheet("Toutes Productions");

            creerFeuilleProductionsBrutes(sheet, productionsPeriode, dateDebut, dateFin, workbook);

            // Sauvegarder le fichier
            try (FileOutputStream fileOut = new FileOutputStream(cheminFichier)) {
                workbook.write(fileOut);
                LOGGER.info(String.format("Export terminé: %d productions exportées vers %s",
                        productionsPeriode.size(), cheminFichier));
            }

        } catch (IOException | SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export Excel", e);
            throw new ServiceException("Erreur lors de la création du fichier Excel: " + e.getMessage());
        }
    }

    private void creerFeuilleProductionsBrutes(Sheet sheet, List<ProductionModel> productions,
                                               LocalDate dateDebut, LocalDate dateFin, Workbook workbook) throws ServiceException {

        // Créer les styles
        CellStyle titleStyle = creerStyleTitre(workbook);
        CellStyle headerStyle = creerStyleEntete(workbook);
        CellStyle dataStyle = creerStyleDonnee(workbook);
        CellStyle dateStyle = creerStyleDate(workbook);
        CellStyle numberStyle = creerStyleNombre(workbook);

        int rowNum = 0;

        // Titre principal
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(String.format("EXPORT COMPLET PRODUCTIONS - %s AU %s",
                dateDebut.format(dateFormatter), dateFin.format(dateFormatter)));
        titleCell.setCellStyle(titleStyle);

        // Infos générales
        rowNum++;
        Row infoRow1 = sheet.createRow(rowNum++);
        infoRow1.createCell(0).setCellValue("Date de génération:");
        infoRow1.createCell(1).setCellValue(LocalDate.now().format(dateFormatter));

        Row infoRow2 = sheet.createRow(rowNum++);
        infoRow2.createCell(0).setCellValue("Nombre de productions:");
        infoRow2.createCell(1).setCellValue(productions.size());

        rowNum += 2; // Espace

        // En-têtes des colonnes - VERSION SIMPLIFIÉE
        Row headerRow = sheet.createRow(rowNum++);
        int colNum = 0;

        headerRow.createCell(colNum++).setCellValue("ID Production");
        headerRow.createCell(colNum++).setCellValue("Matière Première ID");
        headerRow.createCell(colNum++).setCellValue("Nom Matière Première");
        headerRow.createCell(colNum++).setCellValue("Date");
        headerRow.createCell(colNum++).setCellValue("Heure");
        headerRow.createCell(colNum++).setCellValue("Entrée Réelle (kg)");

        // Colonnes dynamiques pour les sorties - on prend le maximum de sorties possible
        int maxSorties = productions.stream()
                .mapToInt(p -> p.getSortiesReelles() != null ? p.getSortiesReelles().size() : 0)
                .max().orElse(0);

        for (int i = 1; i <= Math.max(maxSorties, 10); i++) { // Au moins 10 colonnes de sorties
            headerRow.createCell(colNum++).setCellValue("Sortie " + i + " (kg)");
        }

        headerRow.createCell(colNum++).setCellValue("Total Sorties (kg)");
        headerRow.createCell(colNum++).setCellValue("Statut");
        headerRow.createCell(colNum++).setCellValue("Saisi par");

        // Appliquer le style d'en-tête
        for (int i = 0; i < colNum; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        // Données des productions
        for (ProductionModel production : productions) {
            Row dataRow = sheet.createRow(rowNum++);
            colNum = 0;

            // ID Production
            dataRow.createCell(colNum++).setCellValue(production.getId() != null ? production.getId() : 0);

            // Matière Première ID
            dataRow.createCell(colNum++).setCellValue(production.getMatierePremiereId() != null ? production.getMatierePremiereId() : 0);

            // ✅ CORRECTION : Nom de matière première avec gestion d'erreur simplifiée
            String nomMatiere = "N/A";
            if (production.getMatierePremiereId() != null) {
                try {
                    MatierePremiereService matiereService = new MatierePremiereService();
                    MatierePremiereModel matiere = matiereService.trouverParId(production.getMatierePremiereId());
                    if (matiere != null) {
                        nomMatiere = matiere.getNom();
                    }
                } catch (Exception e) {
                    nomMatiere = "Matière ID: " + production.getMatierePremiereId();
                    LOGGER.warning("Impossible de récupérer le nom de la matière première ID: " + production.getMatierePremiereId());
                }
            }
            dataRow.createCell(colNum++).setCellValue(nomMatiere);

            // Date
            Cell dateCell = dataRow.createCell(colNum++);
            if (production.getDateProduction() != null) {
                dateCell.setCellValue(production.getDateProduction().format(dateFormatter));
            } else {
                dateCell.setCellValue("N/A");
            }
            dateCell.setCellStyle(dateStyle);

            // Heure
            Cell timeCell = dataRow.createCell(colNum++);
            if (production.getHeureProduction() != null) {
                timeCell.setCellValue(production.getHeureProduction().format(timeFormatter));
            } else {
                timeCell.setCellValue("N/A");
            }

            // Entrée réelle
            Cell entreeCell = dataRow.createCell(colNum++);
            entreeCell.setCellValue(production.getQuantiteEntreeReelle());
            entreeCell.setCellStyle(numberStyle);

            // Sorties réelles
            double totalSorties = 0;
            Map<Integer, Double> sortiesMap = new HashMap<>();

            if (production.getSortiesReelles() != null) {
                for (SortieReelle sortie : production.getSortiesReelles()) {
                    sortiesMap.put(sortie.getNumeroSortie(), sortie.getQuantiteReelle());
                    totalSorties += sortie.getQuantiteReelle();
                }
            }

            // Remplir les colonnes de sorties
            for (int i = 1; i <= Math.max(maxSorties, 10); i++) {
                Cell sortieCell = dataRow.createCell(colNum++);
                if (sortiesMap.containsKey(i)) {
                    sortieCell.setCellValue(sortiesMap.get(i));
                } else {
                    sortieCell.setCellValue(0.0);
                }
                sortieCell.setCellStyle(numberStyle);
            }

            // Total sorties
            Cell totalSortiesCell = dataRow.createCell(colNum++);
            totalSortiesCell.setCellValue(totalSorties);
            totalSortiesCell.setCellStyle(numberStyle);

            // Statut
            dataRow.createCell(colNum++).setCellValue(production.getStatut() != null ? production.getStatut().toString() : "N/A");
            dataRow.createCell(colNum++).setCellValue(production.getSaisiPar() != null ? production.getSaisiPar() : "");
        }

        // Redimensionner toutes les colonnes
        for (int i = 0; i < colNum; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    private void creerFeuilleProductions(Sheet sheet, MatierePremiereModel matiere, Workbook workbook) throws ServiceException {
        // Récupérer toutes les productions de cette matière
        List<ProductionModel> productions = productionService.getProductions();

        // Créer les styles
        CellStyle headerStyle = creerStyleEntete(workbook);
        CellStyle dataStyle = creerStyleDonnee(workbook);
        CellStyle dateStyle = creerStyleDate(workbook);
        CellStyle numberStyle = creerStyleNombre(workbook);

        int rowNum = 0;

        // En-têtes des colonnes
        Row headerRow = sheet.createRow(rowNum++);
        int colNum = 0;

        headerRow.createCell(colNum++).setCellValue("ID Production");
        headerRow.createCell(colNum++).setCellValue("Date");
        headerRow.createCell(colNum++).setCellValue("Heure");
        headerRow.createCell(colNum++).setCellValue("Entrée Réelle (kg)");

        // En-têtes dynamiques pour les sorties selon la matière première
        if (matiere.getSortiesIdeales() != null) {
            for (SortieIdeale sortie : matiere.getSortiesIdeales()) {
                headerRow.createCell(colNum++).setCellValue(sortie.getNomSortie() + " Réelle (kg)");
                headerRow.createCell(colNum++).setCellValue(sortie.getNomSortie() + " Idéale (kg)");
                headerRow.createCell(colNum++).setCellValue(sortie.getNomSortie() + " Écart (kg)");
                headerRow.createCell(colNum++).setCellValue(sortie.getNomSortie() + " Performance (%)");
            }
        }

        headerRow.createCell(colNum++).setCellValue("Performance Globale (%)");
        headerRow.createCell(colNum++).setCellValue("Statut");
        headerRow.createCell(colNum++).setCellValue("Saisi par");

        // Appliquer le style d'en-tête
        for (int i = 0; i < colNum; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        // Données des productions
        for (ProductionModel production : productions) {
            if (!production.isComptabilisable()) continue; // Meme regle que les stats a l'ecran

            Row dataRow = sheet.createRow(rowNum++);
            colNum = 0;

            // Données de base
            dataRow.createCell(colNum++).setCellValue(production.getId() != null ? production.getId() : 0);

            Cell dateCell = dataRow.createCell(colNum++);
            if (production.getDateProduction() != null) {
                dateCell.setCellValue(production.getDateProduction().format(dateFormatter));
            }
            dateCell.setCellStyle(dateStyle);

            Cell timeCell = dataRow.createCell(colNum++);
            if (production.getHeureProduction() != null) {
                timeCell.setCellValue(production.getHeureProduction().format(timeFormatter));
            }

            Cell entreeCell = dataRow.createCell(colNum++);
            entreeCell.setCellValue(production.getQuantiteEntreeReelle());
            entreeCell.setCellStyle(numberStyle);

            // Données des sorties
            if (matiere.getSortiesIdeales() != null) {
                for (SortieIdeale sortieIdeale : matiere.getSortiesIdeales()) {
                    // Trouver la sortie réelle correspondante
                    SortieReelle sortieReelle = production.getSortiesReelles().stream()
                            .filter(sr -> sr.getNumeroSortie() == sortieIdeale.getNumeroSortie())
                            .findFirst()
                            .orElse(null);

                    double quantiteReelle = sortieReelle != null ? sortieReelle.getQuantiteReelle() : 0.0;
                    double quantiteIdeale = sortieIdeale.getQuantiteIdeale();
                    double ecart = quantiteReelle - quantiteIdeale;
                    double performance = quantiteIdeale > 0 ? (quantiteReelle / quantiteIdeale) * 100 : 0;

                    // Sortie réelle
                    Cell sortieReelleCell = dataRow.createCell(colNum++);
                    sortieReelleCell.setCellValue(quantiteReelle);
                    sortieReelleCell.setCellStyle(numberStyle);

                    // Sortie idéale
                    Cell sortieIdealeCell = dataRow.createCell(colNum++);
                    sortieIdealeCell.setCellValue(quantiteIdeale);
                    sortieIdealeCell.setCellStyle(numberStyle);

                    // Écart
                    Cell ecartCell = dataRow.createCell(colNum++);
                    ecartCell.setCellValue(ecart);
                    ecartCell.setCellStyle(numberStyle);

                    // Performance
                    Cell perfCell = dataRow.createCell(colNum++);
                    perfCell.setCellValue(performance);
                    perfCell.setCellStyle(numberStyle);
                }
            }

            // Performance globale (calculée)
            double performanceGlobale = calculerPerformanceGlobale(production, matiere);
            Cell perfGlobaleCell = dataRow.createCell(colNum++);
            perfGlobaleCell.setCellValue(performanceGlobale);
            perfGlobaleCell.setCellStyle(numberStyle);

            // Statut
            dataRow.createCell(colNum++).setCellValue(production.getStatut().toString());
            dataRow.createCell(colNum++).setCellValue(production.getSaisiPar() != null ? production.getSaisiPar() : "");
        }

        // Redimensionner toutes les colonnes
        for (int i = 0; i < colNum; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void creerFeuilleStatistiques(Sheet sheet, MatierePremiereModel matiere, Workbook workbook) throws ServiceException {
        List<ProductionModel> productions = productionService.getProductions();
        productions = productions.stream().filter(ProductionModel::isComptabilisable).toList();

        CellStyle headerStyle = creerStyleEntete(workbook);
        CellStyle numberStyle = creerStyleNombre(workbook);

        int rowNum = 0;

        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("STATISTIQUES DE PRODUCTION");
        titleRow.getCell(0).setCellStyle(headerStyle);

        rowNum++;

        // Statistiques générales
        Row statsRow = sheet.createRow(rowNum++);
        statsRow.createCell(0).setCellValue("Nombre total de productions:");
        statsRow.createCell(1).setCellValue(productions.size());

        if (!productions.isEmpty()) {
            // Calculs statistiques
            double totalEntree = productions.stream().mapToDouble(ProductionModel::getQuantiteEntreeReelle).sum();
            double moyenneEntree = totalEntree / productions.size();

            double totalPerformance = productions.stream()
                    .mapToDouble(p -> calculerPerformanceGlobale(p, matiere))
                    .sum();
            double moyennePerformance = totalPerformance / productions.size();

            double maxPerformance = productions.stream()
                    .mapToDouble(p -> calculerPerformanceGlobale(p, matiere))
                    .max().orElse(0);

            double minPerformance = productions.stream()
                    .mapToDouble(p -> calculerPerformanceGlobale(p, matiere))
                    .min().orElse(0);

            // Affichage des statistiques
            Row totalEntreeRow = sheet.createRow(rowNum++);
            totalEntreeRow.createCell(0).setCellValue("Total entrée réelle:");
            Cell totalEntreeCell = totalEntreeRow.createCell(1);
            totalEntreeCell.setCellValue(totalEntree);
            totalEntreeCell.setCellStyle(numberStyle);

            Row moyEntreeRow = sheet.createRow(rowNum++);
            moyEntreeRow.createCell(0).setCellValue("Moyenne entrée par production:");
            Cell moyEntreeCell = moyEntreeRow.createCell(1);
            moyEntreeCell.setCellValue(moyenneEntree);
            moyEntreeCell.setCellStyle(numberStyle);

            rowNum++;

            Row perfMoyRow = sheet.createRow(rowNum++);
            perfMoyRow.createCell(0).setCellValue("Performance moyenne:");
            Cell perfMoyCell = perfMoyRow.createCell(1);
            perfMoyCell.setCellValue(moyennePerformance);
            perfMoyCell.setCellStyle(numberStyle);

            Row perfMaxRow = sheet.createRow(rowNum++);
            perfMaxRow.createCell(0).setCellValue("Meilleure performance:");
            Cell perfMaxCell = perfMaxRow.createCell(1);
            perfMaxCell.setCellValue(maxPerformance);
            perfMaxCell.setCellStyle(numberStyle);

            Row perfMinRow = sheet.createRow(rowNum++);
            perfMinRow.createCell(0).setCellValue("Plus faible performance:");
            Cell perfMinCell = perfMinRow.createCell(1);
            perfMinCell.setCellValue(minPerformance);
            perfMinCell.setCellStyle(numberStyle);
        }

        // Redimensionner les colonnes
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private double calculerPerformanceGlobale(ProductionModel production, MatierePremiereModel matiere) {
        if (matiere.getSortiesIdeales() == null || matiere.getSortiesIdeales().isEmpty()) {
            return 0.0;
        }

        double totalReelle = 0.0;
        double totalIdeale = 0.0;

        for (SortieIdeale sortieIdeale : matiere.getSortiesIdeales()) {
            SortieReelle sortieReelle = production.getSortiesReelles().stream()
                    .filter(sr -> sr.getNumeroSortie() == sortieIdeale.getNumeroSortie())
                    .findFirst()
                    .orElse(null);

            if (sortieReelle != null) {
                totalReelle += sortieReelle.getQuantiteReelle();
            }
            totalIdeale += sortieIdeale.getQuantiteIdeale();
        }

        return totalIdeale > 0 ? (totalReelle / totalIdeale) * 100 : 0.0;
    }

    // Méthodes pour créer les styles
    private CellStyle creerStyleTitre(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle creerStyleEntete(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private CellStyle creerStyleDonnee(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private CellStyle creerStyleDate(Workbook workbook) {
        CellStyle style = creerStyleDonnee(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle creerStyleNombre(Workbook workbook) {
        CellStyle style = creerStyleDonnee(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00"));
        return style;
    }
}