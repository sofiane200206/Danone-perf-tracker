package service;

import model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ProductionService {
    private List<Production> productions;
    private ModeleIdeal modeleIdeal;

    public ProductionService() {
        this.productions = new ArrayList<>();
    }

    public void setModeleIdeal(ModeleIdeal modele) {
        this.modeleIdeal = modele;
    }

    public void ajouterProduction(Production production) {
        if (production != null) {
            productions.add(production);
        }
    }

    public List<Production> getProductions() {
        return new ArrayList<>(productions);
    }

    public List<Production> getProductionsFiltrees(LocalDate dateDebut, LocalDate dateFin) {
        return productions.stream()
                .filter(p -> p.getDate() != null)
                .filter(p -> (dateDebut == null || !p.getDate().isBefore(dateDebut)))
                .filter(p -> (dateFin == null || !p.getDate().isAfter(dateFin)))
                .collect(Collectors.toList());
    }

    public Map<LocalDate, JourneeProduction> grouperParJour(List<Production> productions) {
        Map<LocalDate, JourneeProduction> joursMap = new HashMap<>();

        for (Production production : productions) {
            if (production.getDate() != null) {
                JourneeProduction journee = joursMap.computeIfAbsent(
                        production.getDate(),
                        JourneeProduction::new
                );
                journee.ajouterProduction(production);
                journee.calculerPerformance(modeleIdeal);
            }
        }

        return joursMap;
    }

    public void supprimerProduction(Production production) {
        productions.remove(production);
    }

    public void viderProductions() {
        productions.clear();
    }
}