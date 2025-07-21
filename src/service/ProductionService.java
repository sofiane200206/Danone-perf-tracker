package service;

import model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ProductionService {
    private List<ProductionModel> productions;
    private MatierePremiereModel matierePremiereModel;

    public ProductionService() {
        this.productions = new ArrayList<>();
    }

    public void setMatierePremiereModel(MatierePremiereModel matiere) {
        this.matierePremiereModel = matiere;
    }

    public void ajouterProduction(ProductionModel production) {
        if (production != null) {
            productions.add(production);
        }
    }

    public List<ProductionModel> getProductions() {
        return new ArrayList<>(productions);
    }

    public List<ProductionModel> getProductionsFiltrees(LocalDate dateDebut, LocalDate dateFin) {
        return productions.stream()
                .filter(p -> p.getDateProduction() != null)
                .filter(p -> (dateDebut == null || !p.getDateProduction().isBefore(dateDebut)))
                .filter(p -> (dateFin == null || !p.getDateProduction().isAfter(dateFin)))
                .collect(Collectors.toList());
    }

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

    public void supprimerProduction(ProductionModel production) {
        productions.remove(production);
    }

    public void supprimerProductionParId(Long id) {
        productions.removeIf(p -> Objects.equals(p.getId(), id));
    }

    public void viderProductions() {
        productions.clear();
    }
}