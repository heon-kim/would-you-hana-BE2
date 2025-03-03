package com.hanaro.wouldyouhana.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.hanaro.wouldyouhana.domain.*;
import com.hanaro.wouldyouhana.dto.answer.AnswerGoodRequestDTO;
import com.hanaro.wouldyouhana.dto.answer.AnswerResponseDTO;
import com.hanaro.wouldyouhana.dto.comment.CommentDTO;
import com.hanaro.wouldyouhana.dto.question.*;
import com.hanaro.wouldyouhana.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final CustomerRepository customerRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final AnswerRepository answerRepository;
    private final AnswerGoodRepository answerGoodRepository;
    private final BranchLocationMappingRepository branchLocationMappingRepository;
    private final ImageRepository imageRepository;

    /**
     * 질문(게시글) 등록
     * */
    @Transactional
    public QuestionAllResponseDTO addQuestion(QuestionAddRequestDTO questionAddRequestDTO, List<MultipartFile> files) {

        // 카테고리 ID로 카테고리 객체 가져오기
        Category category = categoryRepository.findByName(questionAddRequestDTO.getCategoryName());

        Customer customer = customerRepository.findById(questionAddRequestDTO.getCustomerId()).get();
        
        // 먼저 Question 엔티티 생성 및 저장
        Question question = Question.builder()
                .customerId(questionAddRequestDTO.getCustomerId())
                .category(category)
                .title(questionAddRequestDTO.getTitle())
                .content(questionAddRequestDTO.getContent())
                .location(questionAddRequestDTO.getLocation())
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                .build();

        // 질문 저장
        Question savedQuestion = questionRepository.save(question);

        // 파일 처리
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                try {
                    // S3에 파일 업로드하고 URL 받아오기
                    String fileUrl = fileStorageService.saveFile(file);
                    
                    // Image 엔티티만 저장
                    Image image = Image.builder()
                        .filePath(fileUrl)
                        .question(savedQuestion)
                        .build();
                    imageRepository.save(image);
                    
                } catch (Exception e) {
                    // 실패 시 트랜잭션 롤백됨
                    throw new RuntimeException("Failed to upload file to S3: " + file.getOriginalFilename(), e);
                }
            }
        }

        // 저장된 이미지 URL들 조회
        List<String> fileUrls = imageRepository.findByQuestion_Id(savedQuestion.getId())
                .stream()
                .map(Image::getFilePath)
                .collect(Collectors.toList());

        return new QuestionAllResponseDTO(
                savedQuestion.getId(),
                customer.getNickname(),
                savedQuestion.getCategory().getName(),
                savedQuestion.getTitle(),
                savedQuestion.getContent(),
                savedQuestion.getLocation(),
                savedQuestion.getCreatedAt(),
                savedQuestion.getUpdatedAt(),
                0L,
                0L,
                0L,
                fileUrls
        );
    }

    /**
     * 질문(게시글) 수정
     * */
    public Long modifyQuestion(QuestionAddRequestDTO questionAddRequestDTO, Long questionId, List<MultipartFile> files) {
        // 기존 질문 엔티티 조회
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        // 카테고리 ID로 카테고리 객체 가져오기
        Category category = categoryRepository.findByName(questionAddRequestDTO.getCategoryName());
        
        // 기존 이미지 삭제
        imageRepository.deleteAllByQuestion_Id(questionId);

        // 새로운 이미지 파일 처리
        if (files != null) {
            for (MultipartFile file : files) {
                String fileUrl = fileStorageService.saveFile(file);
                Image image = Image.builder()
                        .filePath(fileUrl)
                        .question(question)
                        .build();
                imageRepository.save(image);
            }
        }

        // 질문 정보 수정
        question.setTitle(questionAddRequestDTO.getTitle());
        question.setContent(questionAddRequestDTO.getContent());
        question.setLocation(questionAddRequestDTO.getLocation());
        question.setCategory(category);
        question.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        return questionRepository.save(question).getId();
    }

    // 질문 삭제
    public void deleteQuestion(Long questionId) {
        questionRepository.deleteById(questionId);
    }

    // 질문 상세
    public QuestionResponseDTO getOneQuestion(Long questionId) {
        Question foundQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        
        // Image 엔티티로부터 파일 경로 리스트 가져오기
        List<String> filePaths = imageRepository.findByQuestion_Id(questionId)
                .stream()
                .map(Image::getFilePath)
                .collect(Collectors.toList());
        
        Customer customer = customerRepository.findById(foundQuestion.getCustomerId()).get();
        //AnswerGood
        AnswerResponseDTO answerResponseDTO = answerRepository.findByQuestionId(questionId)
                .map(answer -> new AnswerResponseDTO(
                        answer.getBanker().getId(),
                        answer.getBanker().getName(),
                        answer.getId(),
                        answer.getContent(),
                        answer.getCreatedAt(),
                        answer.getUpdatedAt(),
                        answer.getGoodCount()
                ))
                .orElse(null);  // 답변이 없으면 null을 반환

        List<CommentDTO> commentDTOS = foundQuestion.getComments().stream().map(comment ->
        {
            CommentDTO commentDTO = new CommentDTO();
            commentDTO.setId(comment.getId());
            commentDTO.setContent(comment.getContent());
            commentDTO.setCustomerId(comment.getCustomer().getId());
            commentDTO.setNickname(comment.getCustomer().getNickname());
            commentDTO.setCreatedAt(LocalDateTime.now());
            return commentDTO;
        }).collect(Collectors.toList());

        foundQuestion.incrementViewCount();

        return new QuestionResponseDTO(
                foundQuestion.getId(),
                foundQuestion.getCustomerId(),
                customer.getNickname(),
                foundQuestion.getCategory().getName(),
                foundQuestion.getTitle(),
                foundQuestion.getContent(),
                foundQuestion.getLocation(),
                foundQuestion.getCreatedAt(),
                foundQuestion.getUpdatedAt(),
                foundQuestion.getLikeCount(),
                foundQuestion.getScrapCount(),
                foundQuestion.getViewCount(),
                filePaths,
                answerResponseDTO,
                commentDTOS
        );
    }

    // 오늘의 인기 질문
    public List<TodayQnaListDTO> getTodayQuestions(String location) {
        // 오늘 날짜의 시작 시간과 끝 시간을 계산
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();  // 오늘 00:00:00
        LocalDateTime endOfDay = today.atTime(23, 59, 59);  // 오늘 23:59:59

        // 오늘 날짜의 질문들 중에서 viewCount가 높은 6개 가져오기
        List<Question> foundQuestionList = questionRepository.findTop6OrderByViewCountDesc(location, PageRequest.of(0, 6));

        // Question -> TodayQnaListDTO로 변환
        return foundQuestionList.stream()
                .map(question -> new TodayQnaListDTO(
                        question.getId(),
                        question.getTitle(),
                        question.getViewCount()))
                .collect(Collectors.toList());
    }


    // 질문 글 목록 DTO(QnaListDTO) 만드는 공통 메서드
    // 질문 전체 목록, 카테고리별 질문 전체 목록, 고객별 질문 전체 목록에서 사용
    public List<QnaListDTO> makeQnaListDTO(List<Question> fql) {

        List<QnaListDTO> foundQuestionListDTO = fql.stream().map(question -> {

            String answerBanker = Optional.ofNullable(question.getAnswers())
                    .map(answers -> answers.getBanker())
                    .map(banker -> banker.getName())
                    .orElse(null);

            QnaListDTO qnaListDTO = new QnaListDTO();
            qnaListDTO.setQuestionId(question.getId());
            qnaListDTO.setCustomerId(question.getCustomerId());
            qnaListDTO.setAnswerBanker(answerBanker); //이거
            qnaListDTO.setCategoryName(question.getCategory().getName()); //이것도
            qnaListDTO.setTitle((question.getTitle()));
            qnaListDTO.setLocation(question.getLocation());
            qnaListDTO.setCreatedAt(question.getCreatedAt());
            qnaListDTO.setCommentCount(Integer.toUnsignedLong(question.getComments().size()));
            qnaListDTO.setScrapCount(question.getScrapCount());
            qnaListDTO.setViewCount(question.getViewCount());

            return qnaListDTO;
        }).collect(Collectors.toList());
        return foundQuestionListDTO;
    }

    // 지역별 전체 게시글 조회
    public List<QnaListDTO> getAllQuestions(String location) {
        List<Question> foundQuestionList = questionRepository.findByLocation(location);
        return makeQnaListDTO(foundQuestionList);
    }

    public List<QnaListDTO> getAllQuestionsSortedByLatestBranchMapping(String branch){
        // Optional을 사용하여 결과를 안전하게 처리
        Optional<BranchLocationMapping> branchLocationMapping = branchLocationMappingRepository.findByBranchName(branch);

        // Optional이 비어있을 경우 예외를 던지거나 빈 값을 반환하는 처리 필요
        if (branchLocationMapping.isEmpty()) {
            // branchName에 해당하는 BranchLocationMapping이 없을 때 처리
            throw new IllegalArgumentException("Branch name not found: " + branch);
        }

        // BranchLocationMapping에서 location 값을 가져옵니다.
        String location = branchLocationMapping.get().getLocation();

        // location에 해당하는 질문 목록을 조회
        List<Question> foundQuestionList = questionRepository.findByLocation(location);

        // QnaListDTO로 변환하여 반환
        return makeQnaListDTO(foundQuestionList);
    }

    // 지역별 최신순 모든 게시글 조회
    public List<QnaListDTO> getAllQuestionsSortedByLatest(String location) {
        List<Question> foundQuestionList;

        // location이 비어 있지 않으면 해당 location에 맞는 질문을 최신순으로 조회
        if(location.isEmpty()){
            foundQuestionList = questionRepository.findAll(Sort.by(Sort.Order.desc("createdAt")));
        } else {
            foundQuestionList = questionRepository.findByLocationOrderByCreatedAtDesc(location);
        }

        return makeQnaListDTO(foundQuestionList);
    }

    // 지역별 답변 최신순 게시글 3개 조회
    public List<QnaListDTO> get3QuestionsSortedByLatest(String location) {
        List<Question> foundQuestionList;

        if(location.isEmpty()){
            // 모든 Question에서 가장 최근에 답변이 달린 순서로 가져오기
            foundQuestionList = questionRepository.findAll(Sort.by(Sort.Order.desc("answers.createdAt")));
        } else {
            // 특정 지역에 대한 가장 최근에 답변이 달린 Question을 가져오기
            foundQuestionList = questionRepository.findByLocationOrderByAnswersCreatedAtDesc(location);
        }

        // 최신순으로 최대 3개만 가져오기
        List<Question> latestQuestions = foundQuestionList.size() > 3 ? foundQuestionList.subList(0, 3) : foundQuestionList;

        return makeQnaListDTO(latestQuestions);
    }

    // 지역구 랜딩페이지에서 검색 
    public List<QnaListDTO> searchTermFromQuestion(String location, String searchTerm){
        // 지역과 검색어에 맞는 QnA 질문 목록을 조회
        List<Question> foundQuestionList = questionRepository
                .findByLocationAndTitleContainingOrLocationAndContentContaining(location, searchTerm, location, searchTerm);

        // Question을 QnaListDTO로 변환하여 반환
        return makeQnaListDTO(foundQuestionList);
    }

    // 지역별 좋아요 순 게시글 조회
    public List<QnaListDTO> getAllQuestionsSortedByLikes(String location) {
        List<Question> foundQuestionList;
        if(location.isEmpty()){
            foundQuestionList = questionRepository.findAll(Sort.by(Sort.Order.desc("goodCount")));
        }else{
            foundQuestionList = questionRepository.findByLocationOrderByLikeCountDesc(location);
        }
        return makeQnaListDTO(foundQuestionList);
    }

    // 지역별 답변 도움순 게시글 조회
    public List<QnaListDTO> getAllQuestionsSortedByGoodCount(String location){

        List<Question> foundQuestionList;
        if(location.isEmpty()){
            foundQuestionList = questionRepository.findAll();
        }else{
            foundQuestionList = questionRepository.findByLocationOrderByAnswers_GoodCountDesc(location);
        }
        return makeQnaListDTO(foundQuestionList);
    }

    // 카테고리별 질문 전체 목록
    public List<QnaListDTO> getAllQuestionsByCategory(String categoryName, String location) {
        Category category = categoryRepository.findByName(categoryName);
        List<Question> foundQuestionList = questionRepository.findByLocationAndCategory_Id(location, category.getId());
        return makeQnaListDTO(foundQuestionList);
    }

    // 고객별 질문 전체 목록
    public List<QnaListDTO> getAllQuestionsByCustomerId(Long customerId) {
        List<Question> foundQuestionList = questionRepository.findAllByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException("No Question for this customer"));
        return makeQnaListDTO(foundQuestionList);
    }

    // 답변 도움돼요 저장 & 취소
    public void saveGood(AnswerGoodRequestDTO answerGoodRequestDTO){
        Question question = questionRepository.findById(answerGoodRequestDTO.getQuestionId())
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        Customer customer = customerRepository.findById(answerGoodRequestDTO.getCustomerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        Answer answer = question.getAnswers();

        boolean alreadyExists = answerGoodRepository.existsByAnswerAndCustomer(answer, customer);

        if(!alreadyExists){ // 도움
            AnswerGood answerGood = new AnswerGood();
            answerGood.setAnswer(answer);
            answerGood.setCustomer(customer);

            answerGoodRepository.save(answerGood);

            answer.incrementGoodCount();

        }else{ // 도움 취소
            answer.decrementGoodCount();

            AnswerGood answerGood = answerGoodRepository.findByAnswerAndCustomer(answer, customer);

            answerGoodRepository.delete(answerGood);

        }
    }

    // 도움돼요 체크 여부 확인
    public boolean isAnswerGoodChecked(AnswerGoodRequestDTO answerGoodRequestDTO) {
        Question question = questionRepository.findById(answerGoodRequestDTO.getQuestionId())
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        Customer customer = customerRepository.findById(answerGoodRequestDTO.getCustomerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        Answer answer = question.getAnswers();

        return answerGoodRepository.existsByAnswerAndCustomer(answer, customer);
    }


}
