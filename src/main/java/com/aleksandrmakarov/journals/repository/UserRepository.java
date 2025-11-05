package com.aleksandrmakarov.journals.repository;

import java.util.List;
import java.util.Optional;

import com.aleksandrmakarov.journals.model.Participant;
import com.aleksandrmakarov.journals.model.StateType;
import com.aleksandrmakarov.journals.model.User;
import com.aleksandrmakarov.journals.model.UserRole;

/** User repository interface. */
public interface UserRepository {

	Optional<User> findByTelegramId(Long telegramId);

	Optional<User> findByUsername(String username);

	List<User> findAllByRole(UserRole role);

	void upsertState(Long userId, StateType stateType, Long sessionId, Integer questionIndex);

	void upsertStateWithPayload(Long userId, StateType stateType, Long sessionId, Integer questionIndex,
			String payload);

	void clearState(Long userId, boolean isClearQuestionIndex);

	User save(User user);

	List<Participant> findParticipantsOrderedByLastJournal();

	long count();

	void deleteAll();
}
