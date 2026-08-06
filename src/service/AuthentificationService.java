package service;

import dao.UtilisateurDAO;
import model.UserRole;
import model.Utilisateur;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifie les identifiants et cree les comptes.
 *
 * Aucun compte par defaut n'est installe : au premier demarrage, l'application
 * demande la creation du compte administrateur. Un mot de passe livre en dur
 * serait connu de tous et resterait souvent inchange.
 */
public class AuthentificationService {

    private static final Logger LOGGER = Logger.getLogger(AuthentificationService.class.getName());

    private final UtilisateurDAO utilisateurDAO;

    public AuthentificationService() {
        this(new UtilisateurDAO());
    }

    public AuthentificationService(UtilisateurDAO utilisateurDAO) {
        this.utilisateurDAO = utilisateurDAO;
    }

    /** Vrai tant qu'aucun compte n'existe : il faut alors creer l'administrateur. */
    public boolean aucunCompteExistant() throws ServiceException {
        try {
            return utilisateurDAO.compter() == 0;
        } catch (SQLException e) {
            throw new ServiceException("Impossible de consulter les comptes : " + e.getMessage(), e);
        }
    }

    /**
     * Authentifie un utilisateur.
     *
     * @return le compte si les identifiants sont corrects, null sinon
     */
    public Utilisateur authentifier(String identifiant, char[] motDePasse) throws ServiceException {
        if (identifiant == null || identifiant.isBlank() || motDePasse == null || motDePasse.length == 0) {
            return null;
        }

        try {
            Utilisateur utilisateur = utilisateurDAO.trouverParIdentifiant(identifiant.trim());

            if (utilisateur == null || !utilisateur.isActif()) {
                // Message et duree de traitement identiques a un mot de passe faux :
                // inutile d'indiquer si l'identifiant existe.
                LOGGER.info("Echec de connexion pour l'identifiant fourni");
                return null;
            }

            if (!MotDePasseService.verifier(motDePasse, utilisateur.getSel(),
                    utilisateur.getEmpreinteMotDePasse())) {
                LOGGER.info("Echec de connexion pour l'identifiant fourni");
                return null;
            }

            LOGGER.info("Connexion reussie : " + utilisateur.getIdentifiant());
            return utilisateur;

        } catch (SQLException e) {
            throw new ServiceException("Verification des identifiants impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Cree un compte apres controle de l'identifiant et de la robustesse du mot de passe.
     */
    public Utilisateur creerCompte(String identifiant, char[] motDePasse, UserRole role)
            throws ServiceException {

        if (identifiant == null || identifiant.isBlank()) {
            throw new ServiceException("L'identifiant est obligatoire.");
        }
        if (role == null) {
            throw new ServiceException("Le role est obligatoire.");
        }

        String identifiantNettoye = identifiant.trim();
        if (identifiantNettoye.length() < 3) {
            throw new ServiceException("L'identifiant doit contenir au moins 3 caracteres.");
        }

        String refus = MotDePasseService.motifDeRefus(motDePasse == null ? null : new String(motDePasse));
        if (refus != null) {
            throw new ServiceException(refus);
        }

        try {
            if (utilisateurDAO.identifiantExiste(identifiantNettoye)) {
                throw new ServiceException("Cet identifiant est deja utilise.");
            }

            String sel = MotDePasseService.genererSel();
            String empreinte = MotDePasseService.hacher(motDePasse, sel);

            Utilisateur utilisateur = new Utilisateur(identifiantNettoye, empreinte, sel, role);
            utilisateurDAO.creer(utilisateur);
            return utilisateur;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Creation de compte impossible", e);
            throw new ServiceException("Creation du compte impossible : " + e.getMessage(), e);
        }
    }

    /** Change le mot de passe apres verification de l'actuel. */
    public void changerMotDePasse(String identifiant, char[] ancien, char[] nouveau)
            throws ServiceException {

        Utilisateur utilisateur = authentifier(identifiant, ancien);
        if (utilisateur == null) {
            throw new ServiceException("Mot de passe actuel incorrect.");
        }

        String refus = MotDePasseService.motifDeRefus(nouveau == null ? null : new String(nouveau));
        if (refus != null) {
            throw new ServiceException(refus);
        }

        try {
            String sel = MotDePasseService.genererSel();
            utilisateurDAO.mettreAJourMotDePasse(
                    utilisateur.getId(), MotDePasseService.hacher(nouveau, sel), sel);
        } catch (SQLException e) {
            throw new ServiceException("Changement de mot de passe impossible : " + e.getMessage(), e);
        }
    }
}
