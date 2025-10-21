package com.aleksandrmakarov.journals.repository;

import com.aleksandrmakarov.journals.model.Participant;
import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;
import java.util.List;
import java.util.Optional;

/**
 * Interface for UserRepository to enable JDK dynamic proxies in GraalVM native images.
 * This avoids CGLIB proxy issues with GraalVM native images.
 */
public interface UserRepositoryInterface {
    
    Optional<User> findByTelegramId(Long telegramId);
    
    Optional<User> findByUsername(String username);
    
    List<User> findAllByRole(UserRole role);
    
    void upsertState(Long userId, StateType stateType, Long sessionId, Integer questionIndex);
    
    void clearState(Long userId, boolean isClearQuestionIndex);
    
    User save(User user);
    
    List<Participant> findParticipantsOrderedByLastJournal();
    
    long count();
    
    void deleteAll();
}
