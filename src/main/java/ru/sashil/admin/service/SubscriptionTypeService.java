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
        // Проверяем уникальность displayName
        if (type.getId() == null && repository.existsByDisplayName(type.getDisplayName())) {
            throw new IllegalArgumentException("Тип абонемента с названием \"" + type.getDisplayName() + "\" уже существует.");
        }
        // При редактировании тоже проверяем, но исключаем саму запись
        if (type.getId() != null) {
            Optional<SubscriptionType> existing = repository.findByDisplayName(type.getDisplayName());
            if (existing.isPresent() && !existing.get().getId().equals(type.getId())) {
                throw new IllegalArgumentException("Тип абонемента с названием \"" + type.getDisplayName() + "\" уже существует.");
            }
        }
        return repository.save(type);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}