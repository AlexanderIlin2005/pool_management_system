package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.repository.PoolRepository;

import java.util.List;
import java.util.Optional;

@Service
public class PoolService {

    @Autowired
    private PoolRepository poolRepository;

    public List<Pool> getAllPools() {
        return poolRepository.findAll();
    }

    public Optional<Pool> getPoolById(Long id) {
        return poolRepository.findById(id);
    }

    public void savePool(Pool pool) {
        poolRepository.save(pool);
    }

    public void deletePool(Long id) {
        poolRepository.deleteById(id);
    }
}