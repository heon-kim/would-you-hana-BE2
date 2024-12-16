package com.hanaro.wouldyouhana.service;

import com.hanaro.wouldyouhana.domain.Answer;
import com.hanaro.wouldyouhana.domain.Banker;
import com.hanaro.wouldyouhana.domain.Question;
import com.hanaro.wouldyouhana.dto.answer.AnswerAddRequestDTO;
import com.hanaro.wouldyouhana.dto.answer.AnswerResponseDTO;
import com.hanaro.wouldyouhana.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnswerService {

    private final CommentRepository commentRepository;
    private final QuestionRepository questionRepository;
    private final CustomerRepository customerRepository;
    private final BankerRepository bankerRepository;
    private final AnswerRepository answerRepository;

    @Autowired
    public AnswerService(CustomerRepository customerRepository, QuestionRepository questionRepository, CommentRepository commentRepository, BankerRepository bankerRepository, AnswerRepository answerRepository) {
        this.customerRepository = customerRepository;
        this.questionRepository = questionRepository;
        this.commentRepository = commentRepository;
        this.bankerRepository = bankerRepository;
        this.answerRepository = answerRepository;
    }

    // 답변 추가
    public AnswerResponseDTO addAnswer(Long questionId, @Valid AnswerAddRequestDTO answerAddRequestDTO) {
        Question foundQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("question not found"));

        Banker foundBanker = bankerRepository.findById(answerAddRequestDTO.getBankerId())
                .orElseThrow(() -> new EntityNotFoundException("banker not found"));


        Answer answer = Answer.builder()
                .banker(foundBanker)
                .question(foundQuestion)
                .content(answerAddRequestDTO.getContent())
                .createdAt(LocalDateTime.now())
                .build();

        foundQuestion.setAnswers(answer);
        Answer addedAnswer = answerRepository.save(answer);

        return new AnswerResponseDTO(
                addedAnswer.getBanker().getId(),
                addedAnswer.getQuestion().getId(),
                addedAnswer.getContent(),
                addedAnswer.getCreatedAt(),
                addedAnswer.getUpdatedAt()
        );
    }

    // 답변 수정
    public AnswerResponseDTO updateAnswer(Long answerId, @Valid AnswerAddRequestDTO answerAddRequestDTO) {
        Answer foundAnswer = answerRepository.findById(answerId).orElseThrow(() -> new EntityNotFoundException("answer not found"));

        foundAnswer.setContent(answerAddRequestDTO.getContent());
        foundAnswer.setUpdatedAt(LocalDateTime.now());

        Answer updatedAnswer = answerRepository.save(foundAnswer);

        return new AnswerResponseDTO(
                updatedAnswer.getBanker().getId(),
                updatedAnswer.getQuestion().getId(),
                updatedAnswer.getContent(),
                updatedAnswer.getCreatedAt(),
                updatedAnswer.getUpdatedAt()
        );
    }

    // 답변 삭제
    public void deleteAnswer(Long answerId) {
        answerRepository.deleteById(answerId);
    }
}