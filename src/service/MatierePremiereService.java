package service;

import dao.MatierePremiereDAO;
import model.MatierePremiereModel;
import model.MatierePremiereModel.SortieIdeale;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class MatierePremiereService {
    private static final Logger LOGGER = Logger.getLogger(MatierePremiereService.class.getName());
    private final MatierePremiereDAO matiereDAO;

    public MatierePremiereService() {
        this.matiereDAO = new MatierePremiereDAO();
    }

    /**
     * Crée une nouvelle matière première avec validation
     */
    public MatierePremiereModel creerMatierePremiereComplete(String nom, double quantiteEntreeIdeale,
                                                             int nombreSorties, List<SortieIdeale> sortiesIdeales)
            throws ServiceException {
        try {
            // Validation des données
            validerDonneesMatiere(nom, quantiteEntreeIdeale, nombreSorties, sortiesIdeales);

            // Vérifier l'unicité du nom
            if (!nomEstUnique(nom, null)) {
                throw new ServiceException("Une matière première avec ce nom existe déjà");
            }

            // Créer le modèle
            MatierePremiereModel matiere = new MatierePremiereModel(nom, quantiteEntreeIdeale, nombreSorties);
            matiere.setSortiesIdeales(sortiesIdeales);

            // Validation finale
            if (!matiere.isValide()) {
                throw new ServiceException("Les données de la matière première sont invalides");
            }

            // Sauvegarder en base
            Long id = matiereDAO.creer(matiere);
            matiere.setId(id);

            LOGGER.info("Matière première créée avec succès : " + nom);
            return matiere;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la création de la matière première", e);
            throw new ServiceException("Erreur lors de la sauvegarde : " + e.getMessage(), e);
        }
    }

    /**
     * Crée une matière première avec 2 sorties par défaut (compatibilité)
     */
    public MatierePremiereModel creerMatierePremiereSimple(String nom, double quantiteEntreeIdeale,
                                                           double sortie1, double sortie2) throws ServiceException {
        List<SortieIdeale> sorties = List.of(
                new SortieIdeale(1, sortie1, "Sortie 1"),
                new SortieIdeale(2, sortie2, "Sortie 2")
        );

        return creerMatierePremiereComplete(nom, quantiteEntreeIdeale, 2, sorties);
    }

    /**
     * Récupère toutes les matières premières actives
     */
    public List<MatierePremiereModel> listerMatieresActives() throws ServiceException {
        try {
            List<MatierePremiereModel> matieres = matiereDAO.listerActives();
            LOGGER.info("Récupération de " + matieres.size() + " matières premières actives");
            return matieres;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des matières premières", e);
            throw new ServiceException("Erreur lors de la récupération des données : " + e.getMessage(), e);
        }
    }
    /**
     * Vérifie si une matière première avec ce nom existe déjà
     */
    public boolean nomExiste(String nom) throws ServiceException {
        try {
            List<MatierePremiereModel> matieres = listerMatieresActives();
            return matieres.stream()
                    .anyMatch(matiere -> matiere.getNom().equalsIgnoreCase(nom.trim()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la vérification du nom", e);
            throw new ServiceException("Impossible de vérifier l'unicité du nom : " + e.getMessage());
        }
    }
    /**
     * Trouve une matière première par son ID
     */
    public MatierePremiereModel trouverParId(Long id) throws ServiceException {
        try {
            if (id == null) {
                throw new ServiceException("L'ID de la matière première ne peut pas être null");
            }

            MatierePremiereModel matiere = matiereDAO.trouverParId(id);
            if (matiere == null) {
                throw new ServiceException("Matière première introuvable avec l'ID : " + id);
            }

            return matiere;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la recherche de la matière première", e);
            throw new ServiceException("Erreur lors de la recherche : " + e.getMessage(), e);
        }
    }

    /**
     * Trouve une matière première par son nom
     */
    public MatierePremiereModel trouverParNom(String nom) throws ServiceException {
        try {
            if (nom == null || nom.trim().isEmpty()) {
                throw new ServiceException("Le nom de la matière première ne peut pas être vide");
            }

            List<MatierePremiereModel> matieres = matiereDAO.listerActives();
            return matieres.stream()
                    .filter(m -> m.getNom().equalsIgnoreCase(nom.trim()))
                    .findFirst()
                    .orElse(null);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la recherche de la matière première par nom", e);
            throw new ServiceException("Erreur lors de la recherche : " + e.getMessage(), e);
        }
    }
    public void supprimerDefinitivement(Long id) throws ServiceException {
        try {
            if (id == null) {
                throw new ServiceException("L'ID de la matière première ne peut pas être null");
            }

            // Récupérer la matière première pour avoir son nom (pour les logs)
            MatierePremiereModel matiere = matiereDAO.trouverParId(id);
            if (matiere == null) {
                throw new ServiceException("Matière première introuvable avec l'ID : " + id);
            }

            // 1. Supprimer toutes les productions liées (CASCADE)
            ProductionService productionService = new ProductionService();
            int nbProductionsSupprimees = productionService.supprimerToutesProductionsMatiere(id);

            // 2. Supprimer la matière première
            matiereDAO.supprimerDefinitivement(id);

            LOGGER.info("Matière première '" + matiere.getNom() + "' supprimée définitivement avec "
                    + nbProductionsSupprimees + " productions associées");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression définitive de la matière première", e);
            throw new ServiceException("Erreur lors de la suppression : " + e.getMessage(), e);
        }
    }
    public void supprimerAvecConfirmation(Long id, boolean confirmationUtilisateur) throws ServiceException {
        if (!confirmationUtilisateur) {
            throw new ServiceException("Suppression annulée par l'utilisateur");
        }

        // Compter les productions associées pour informer l'utilisateur
        int nbProductions = compterProductionsAssociees(id);
        if (nbProductions > 0) {
            LOGGER.warning("Attention : " + nbProductions + " productions seront supprimées");
        }

        supprimerDefinitivement(id);
    }
    public int compterProductionsAssociees(Long matiereId) throws ServiceException {
        try {
            if (matiereId == null) {
                return 0;
            }

            ProductionService productionService = new ProductionService();
            return productionService.compterProductionsParMatiere(matiereId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors du comptage des productions associées", e);
            return 0; // En cas d'erreur, on retourne 0 pour ne pas bloquer
        }
    }
    /**
     * Met à jour une matière première existante
     */
    public void mettreAJour(MatierePremiereModel matiere) throws ServiceException {
        try {
            if (matiere == null || matiere.getId() == null) {
                throw new ServiceException("Matière première invalide pour la mise à jour");
            }

            // Vérifier l'unicité du nom (exclure l'ID actuel)
            if (!nomEstUnique(matiere.getNom(), matiere.getId())) {
                throw new ServiceException("Une autre matière première avec ce nom existe déjà");
            }

            if (!matiere.isValide()) {
                throw new ServiceException("Les données de la matière première sont invalides");
            }

            matiereDAO.mettreAJour(matiere);
            LOGGER.info("Matière première mise à jour : " + matiere.getNom());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour de la matière première", e);
            throw new ServiceException("Erreur lors de la mise à jour : " + e.getMessage(), e);
        }
    }

    /**
     * Désactive une matière première (soft delete)
     */
    public void desactiver(Long id) throws ServiceException {
        try {
            if (id == null) {
                throw new ServiceException("L'ID de la matière première ne peut pas être null");
            }

            matiereDAO.desactiver(id);
            LOGGER.info("Matière première désactivée : ID " + id);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la désactivation de la matière première", e);
            throw new ServiceException("Erreur lors de la désactivation : " + e.getMessage(), e);
        }
    }

    /**
     * Réactive une matière première
     */
    public void reactiver(Long id) throws ServiceException {
        try {
            if (id == null) {
                throw new ServiceException("L'ID de la matière première ne peut pas être null");
            }

            // Récupérer la matière première pour la réactiver
            MatierePremiereModel matiere = matiereDAO.trouverParId(id);
            if (matiere == null) {
                throw new ServiceException("Matière première introuvable avec l'ID : " + id);
            }

            matiere.setActif(true);
            matiereDAO.mettreAJour(matiere);
            LOGGER.info("Matière première réactivée : " + matiere.getNom());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la réactivation de la matière première", e);
            throw new ServiceException("Erreur lors de la réactivation : " + e.getMessage(), e);
        }
    }

    /**
     * Valide si un nom de matière première est unique
     */
    public boolean nomEstUnique(String nom, Long idExclu) throws ServiceException {
        try {
            if (nom == null || nom.trim().isEmpty()) {
                return false;
            }

            List<MatierePremiereModel> matieres = matiereDAO.listerActives();
            return matieres.stream()
                    .filter(m -> idExclu == null || !m.getId().equals(idExclu))
                    .noneMatch(m -> m.getNom().equalsIgnoreCase(nom.trim()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la validation de l'unicité du nom", e);
            throw new ServiceException("Erreur lors de la validation : " + e.getMessage(), e);
        }
    }

    /**
     * Valide les données d'une matière première
     */
    private void validerDonneesMatiere(String nom, double quantiteEntreeIdeale,
                                       int nombreSorties, List<SortieIdeale> sortiesIdeales) throws ServiceException {
        if (nom == null || nom.trim().isEmpty()) {
            throw new ServiceException("Le nom de la matière première est obligatoire");
        }

        if (nom.trim().length() > 100) {
            throw new ServiceException("Le nom de la matière première ne peut pas dépasser 100 caractères");
        }

        if (quantiteEntreeIdeale <= 0) {
            throw new ServiceException("La quantité d'entrée idéale doit être positive");
        }

        if (nombreSorties <= 0 || nombreSorties > 10) {
            throw new ServiceException("Le nombre de sorties doit être entre 1 et 10");
        }

        if (sortiesIdeales == null || sortiesIdeales.size() != nombreSorties) {
            throw new ServiceException("Le nombre de sorties idéales doit correspondre au nombre de sorties déclaré");
        }

        // Vérifier que les numéros de sorties sont cohérents
        for (int i = 1; i <= nombreSorties; i++) {
            final int numeroSortie = i;
            boolean sortieExiste = sortiesIdeales.stream()
                    .anyMatch(s -> s.getNumeroSortie() == numeroSortie);
            if (!sortieExiste) {
                throw new ServiceException("La sortie numéro " + i + " est manquante");
            }
        }

        // Vérifier que toutes les quantités sont positives ou nulles
        for (SortieIdeale sortie : sortiesIdeales) {
            if (sortie.getQuantiteIdeale() < 0) {
                throw new ServiceException("Les quantités idéales ne peuvent pas être négatives");
            }
        }
    }

    /**
     * Compte le nombre de matières premières actives
     */
    public int compterMatieresActives() throws ServiceException {
        try {
            return matiereDAO.listerActives().size();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du comptage des matières premières", e);
            throw new ServiceException("Erreur lors du comptage : " + e.getMessage(), e);
        }
    }
}