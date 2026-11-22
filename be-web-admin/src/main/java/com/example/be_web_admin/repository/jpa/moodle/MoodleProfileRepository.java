package com.example.be_web_admin.repository.jpa.moodle;

import com.example.be_web_admin.model.jpa.moodle.MoodleProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface MoodleProfileRepository extends JpaRepository<MoodleProfile, Long> {

    List<MoodleProfile> findAllByOrderByProfileNameAsc();

    Optional<MoodleProfile> findFirstByIsDefaultTrue();

    boolean existsByProfileName(String profileName);
}
