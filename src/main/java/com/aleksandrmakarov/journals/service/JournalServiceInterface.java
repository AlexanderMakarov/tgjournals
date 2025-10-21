package com.aleksandrmakarov.journals.service;

import com.aleksandrmakarov.journals.model.Journal;
import com.aleksandrmakarov.journals.model.Question;
import com.aleksandrmakarov.journals.model.Session;
import com.aleksandrmakarov.journals.model.User;
import java.util.List;

/**
 * Interface for JournalService to enable JDK dynamic proxies in GraalVM native images.
 * This avoids CGLIB proxy issues with GraalVM native images.
 */
public interface JournalServiceInterface {
    
    Journal saveJournal(String answer, User user, Session session, Question question);
    
    List<Journal> getUserJournals(User user, int limit);
    
    List<Journal> getUserJournalsForSession(User user, Session session);
    
    Long getUserJournalCount(User user);
}
