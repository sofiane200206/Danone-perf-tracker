package service;

import dao.ProductionDAO;
import model.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ProductionService {
    private static final Logger LOGGER = Logger.getLogger(ProductionService.class.getName());

    private ProductionDAO productionDAO;
    private MatierePremiereModel matierePremiereModel;

    public ProductionService() {
        this.productionDAO = new ProductionDAO();
    }

    public void setMatierePremiereModel(MatierePremiereModel matiere) {
        this.matierePremiereModel = matiere;
    }

    /**
     * Ajouter une production et la sauvegarder en base
     */
    public void ajouterProduction(ProductionModel production) throws ServiceException {
        if (production == null) {
            throw new ServiceException("La production ne peut pas être null");
        }

        try {
            // Valider la production avant sauvegarde
            if (production.isDonneeComplete()) {
                production.validerProduction();

                // Sauvegarder en base de données
                Long id = productionDAO.creer(production);
                production.setId(id);

                LOGGER.info("Production sauvegardée avec ID : " + id);
            } else {
                LOGGER.warning("Production incomplète, non sauvegardée : " + production.getId());
                throw new ServiceException("Production incomplète, impossible de sauvegarder");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la sauvegarde de la production", e);
            throw new ServiceException("Erreur lors de la sauvegarde : " + e.getMessage(), e);
        }
    }

    /**
     * Récupérer toutes les productions pour la matière première actuelle
     */
    public List<ProductionModel> getProductions() {
        if (matierePremiereModel == null) {
            return new ArrayList<>();
        }

        try {
            return productionDAO.listerParMatiere(matierePremiereModel.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des productions", e);
            return new ArrayList<>();
        }
    }

    /**
     * Récupérer les productions filtrées par date
     */
    public List<ProductionModel> getProductionsFiltrees(LocalDate dateDebut, LocalDate dateFin) {
        if (matierePremiereModel == null) {
            return new ArrayList<>();
        }

        try {
            if (dateDebut != null && dateFin != null) {
                return productionDAO.listerParMatierePeriode(matierePremiereModel.getId(), dateDebut, dateFin);
            } else {
                return getProductions().stream()
                        .filter(p -> p.getDateProduction() != null)
                        .filter(p -> (dateDebut == null || !p.getDateProduction().isBefore(dateDebut)))
                        .filter(p -> (dateFin == null || !p.getDateProduction().isAfter(dateFin)))
                        .collect(Collectors.toList());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du filtrage des productions", e);
            return new ArrayList<>();
        }
    }

    /**
     * Grouper les productions par jour
     */
    public Map<LocalDate, JourneeProduction> grouperParJour(List<ProductionModel> productions) {
        Map<LocalDate, JourneeProduction> joursMap = new HashMap<>();

        for (ProductionModel production : productions) {
            if (production.getDateProduction() != null) {
                JourneeProduction journee = joursMap.computeIfAbsent(
                        production.getDateProduction(),
                        JourneeProduction::new
                );
                journee.ajouterProduction(production);
                journee.calculerPerformance(matierePremiereModel);
            }
        }

        return joursMap;
    }

    /**
     * Supprimer une production
     */
    public void supprimerProduction(ProductionModel production) throws ServiceException {
        if (production == null || production.getId() == null) {
            throw new ServiceException("Production invalide");
        }

        try {
            productionDAO.supprimer(production.getId());
            LOGGER.info("Production supprimée : ID " + production.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression", e);
            throw new ServiceException("Erreur lors de la suppression : " + e.getMessage(), e);
        }
    }

    /**
     * Supprimer une production par ID
     */
    public void supprimerProductionParId(Long id) throws ServiceException {
        if (id == null) {
            throw new ServiceException("ID invalide");
        }

        try {
            productionDAO.supprimer(id);
            LOGGER.info("Production supprimée : ID " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression", e);
            throw new ServiceException("Erreur lors de la suppression : " + e.getMessage(), e);
        }
    }
    public int compterProductionsParMatiere(Long matiereId) throws ServiceException {
        try {
            if (matiereId == null) {
                return 0;
            }

            return productionDAO.compterParMatiereId(matiereId);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du comptage des productions", e);
            throw new ServiceException("Erreur lors du comptage des productions : " + e.getMessage(), e);
        }
    }



    /**
     * Mettre à jour une production
     */
    public void mettreAJourProduction(ProductionModel production) throws ServiceException {
        if (production == null || production.getId() == null) {
            throw new ServiceException("Production invalide");
        }

        try {
            // Valider avant mise à jour
            if (production.isDonneeComplete()) {
                production.validerProduction();
                productionDAO.mettreAJour(production);
                LOGGER.info("Production mise à jour : ID " + production.getId());
            } else {
                throw new ServiceException("Production incomplète, impossible de mettre à jour");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour", e);
            throw new ServiceException("Erreur lors de la mise à jour : " + e.getMessage(), e);
        }
    }
    public int supprimerToutesProductionsMatiere(Long matiereId) throws ServiceException {
        if (matiereId == null) {
            throw new ServiceException("ID de matière première invalide");
        }

        try {
            // Récupérer toutes les productions de cette matière
            List<ProductionModel> productions = productionDAO.listerParMatiere(matiereId);
            int nbSupprimees = 0;

            for (ProductionModel production : productions) {
                if (production.getId() != null) {
                    productionDAO.supprimer(production.getId());
                    nbSupprimees++;
                    LOGGER.info("Production ID " + production.getId() + " supprimée (cascade)");
                }
            }

            LOGGER.info("Suppression cascade terminée : " + nbSupprimees + " productions supprimées pour la matière " + matiereId);
            return nbSupprimees;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression cascade des productions", e);
            throw new ServiceException("Erreur lors de la suppression cascade : " + e.getMessage(), e);
        }
    }
    /**
     * Vider toutes les productions (pour tests uniquement)
     */
    public void viderProductions() {
        LOGGER.warning("Attention : cette méthode ne vide que le cache, pas la base de données");
        // Cette méthode était utilisée pour vider la liste en mémoire
        // Maintenant on charge depuis la base, donc pas besoin
    }
}