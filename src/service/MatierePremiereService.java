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