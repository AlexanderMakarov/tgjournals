package com.aleksandrmakarov.journals.model;

/** Single state type for a user. Only one at a time is supported. */
public enum StateType {
  /** Player is answering on questions in QA flow. */
  QA_FLOW,
  /** Admin is updating questions. */
  QUESTIONS_UPDATE
}
