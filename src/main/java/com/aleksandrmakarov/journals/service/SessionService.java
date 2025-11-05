package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.SessionJournals;
import java.util.List;

/** Session service interface. */
public interface SessionService {

	Session getActiveSession();

	Session createNewSession(String name);

	Session finishActiveSession();

	void updateSessionQuestions(Session session, List<Question> questions);

	List<Question> getQuestions(Long sessionId);

	List<SessionJournals> getJournalsForLastSessions(Long userId, int limitLastSessions);
}
