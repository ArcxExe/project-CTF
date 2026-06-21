package com.arcx.ctfplatform.common.security;

import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        com.arcx.ctfplatform.users.entity.User user = userRepository.findByEmail(normalized)
                .or(() -> userRepository.findByUsername(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (user.getRole() == com.arcx.ctfplatform.users.entity.Role.STUDENT) {
            studentRepository.findByUserId(user.getId()).ifPresent(student -> {
                if (student.getStatus() == com.arcx.ctfplatform.academic.entity.StudentStatus.BLOCKED ||
                    student.getStatus() == com.arcx.ctfplatform.academic.entity.StudentStatus.DISQUALIFIED) {
                    throw new UsernameNotFoundException("Student is blocked or disqualified");
                }
            });
        }

        return user;
    }
}
