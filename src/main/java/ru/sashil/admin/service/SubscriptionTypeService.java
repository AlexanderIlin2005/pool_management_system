package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.SubscriptionType;
import ru.sashil.admin.repository.SubscriptionTypeRepository;
import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionTypeService {

    @Autowired
    private SubscriptionTypeRepository repository;

    public List<SubscriptionType> getAll() {
        return repository.findAll();
    }

    public Optional<SubscriptionType> getById(Long id) {
        return repository.findById(id);
    }

    public SubscriptionType save(SubscriptionType type) {
        if (type.getId() == null && repository.existsByName(type.getName())) {
            throw new IllegalArgumentException("Тип абонемента с кодом \"" + type.getName() + "\" уже существует.");
        }
        return repository.save(type);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}