package com.gsg.youtubemonitor.service;

import com.gsg.youtubemonitor.common.YMException;
import com.gsg.youtubemonitor.common.YMExceptionReason;
import com.gsg.youtubemonitor.dto.user.UserDto;
import com.gsg.youtubemonitor.dto.user.UserHelper;
import com.gsg.youtubemonitor.model.User;
import com.gsg.youtubemonitor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceBean implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceBean.class);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceBean(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDto getUserDto(String username) {
        return userRepository.findByUsername(username)
                .map(UserHelper::toDto)
                .orElse(null);
    }

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) throws YMException {
        log.debug("Creating User[{}]", userDto.toString());
        if (userDto.getId() != 0) {
            Optional<User> userWithId = userRepository.findById(userDto.getId());
            if (userWithId.isPresent()) {
                String errorMessage = String.format("User already exists with id[%d]", userDto.getId());
                log.error(errorMessage);
                throw new YMException(YMExceptionReason.BAD_REQUEST, errorMessage);
            }
            userDto.setId(0);
        }
        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            String errorMessage = String.format("Bad request to create user[%s] user already exists with this username", userDto);
            log.error(errorMessage);
            throw new YMException(YMExceptionReason.BAD_REQUEST, errorMessage);
        }
        User user = UserHelper.fromDto(userDto);
        user.setPasswordHash(passwordEncoder.encode(userDto.getPassword()));
        user.setNextJobRunTime(LocalDateTime.now());
        user = userRepository.save(user);
        return UserHelper.toDto(user);
    }

    @Override
    @Transactional
    public void updateUser(int userId, String countryCode, Integer jobRunMinute) throws YMException {
        log.debug("Updating user[{}] countryCode[{}] jobRunMinute[{}]", userId, countryCode, jobRunMinute);
        if (countryCode == null && jobRunMinute == null) {
            throw new YMException(YMExceptionReason.BAD_REQUEST, "either countryCode or jobRunMinute should be present for user update");
        }
        if (countryCode != null && countryCode.length() != 2) {
            throw new YMException(YMExceptionReason.BAD_REQUEST, "country code length should be 2");
        }
        if (jobRunMinute != null && (jobRunMinute <= 0 || jobRunMinute > 60)) {
            throw new YMException(YMExceptionReason.BAD_REQUEST, "jobRunMinute should be in range 1-60 inclusive");
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            String errorMessage = String.format("No user[%d] found to update countryCode[%s]", userId, countryCode);
            log.error(errorMessage);
            throw new YMException(YMExceptionReason.BAD_REQUEST, errorMessage);
        }

        User user = userOpt.get();
        if (countryCode != null) {
            user.setCountryCode(countryCode);
        }
        if (jobRunMinute != null) {
            user.setJobRunMinute(jobRunMinute);
            user.updateNextJobRunTime();
        }
        userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByUsername(username);
        if (!user.isPresent()) {
            throw new UsernameNotFoundException("User not found.");
        }
        org.springframework.security.core.userdetails.User.UserBuilder builder;
        builder = org.springframework.security.core.userdetails.User.withUsername(user.get().getUsername());
        return builder.password(user.get().getPasswordHash())
                .roles("USER")
                .build();
    }

    @Override
    public List<Integer> getUserIdsToRunJobs() {
        return userRepository.findUserIdsByNextJobRunTimeBefore(LocalDateTime.now());
    }

    @Override
    public User getUser(int id) {
        return userRepository.findById(id)
                             .orElse(null);
    }
}
