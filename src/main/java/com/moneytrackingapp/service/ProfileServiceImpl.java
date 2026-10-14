package com.moneytrackingapp.service;

import com.moneytrackingapp.model.Profile;
import com.moneytrackingapp.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {
    private static final long PROFILE_ID = 1L;

    private final ProfileRepository repository;

    public ProfileServiceImpl(ProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Profile getProfile() {
        return repository.findById(PROFILE_ID).orElseGet(() -> {
            Profile created = new Profile();
            created.setId(PROFILE_ID);
            return repository.save(created);
        });
    }

    @Override
    @Transactional
    public Profile updateProfile(Profile profile) {
        Profile existing = getProfile();
        existing.setCurrencyCode(profile.getCurrencyCode());
        existing.setCurrencySymbol(profile.getCurrencySymbol());
        return repository.save(existing);
    }
}
