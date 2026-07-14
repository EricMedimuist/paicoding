package com.github.paicoding.forum.service.article.service.impl;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.enums.*;
import com.github.paicoding.forum.api.model.event.ArticleMsgEvent;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.article.ArticlePostReq;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.core.senstive.SensitiveService;
import com.github.paicoding.forum.core.util.NumUtil;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.core.util.UrlSlugUtil;
import com.github.paicoding.forum.core.util.id.IdUtil;
import com.github.paicoding.forum.service.article.conveter.ArticleConverter;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.dao.ArticleTagDao;
import com.github.paicoding.forum.service.article.repository.dao.ColumnDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleWriteService;
import com.github.paicoding.forum.service.article.service.ColumnSettingService;
import com.github.paicoding.forum.service.article.service.SlugGeneratorService;
import com.github.paicoding.forum.service.image.service.ImageService;
import com.github.paicoding.forum.service.user.service.AuthorWhiteListService;
import com.github.paicoding.forum.service.user.service.UserFootService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 文章操作相关服务类
 *
 * @author louzai
 * @date 2022-07-20
 */
@Slf4j
@Service
public class ArticleWriteServiceImpl implements ArticleWriteService {

    private static final int ARTICLE_SLUG_MAX_LENGTH = 100;

    private final ArticleDao articleDao;

    private final ArticleTagDao articleTagDao;

    @Autowired
    private ColumnSettingService columnSettingService;

    @Autowired
    private ColumnDao columnDao;

    @Autowired
    private SlugGeneratorService slugGeneratorService;

    @Autowired
    private UserFootService userFootService;

    @Autowired
    private ImageService imageService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AuthorWhiteListService articleWhiteListService;

    @Autowired
    private SensitiveService sensitiveService;

    // 构造方法的注入方式
    public ArticleWriteServiceImpl(ArticleDao articleDao, ArticleTagDao articleTagDao) {
        this.articleDao = articleDao;
        this.articleTagDao = articleTagDao;
    }

    /**
     * 保存文章，当articleId存在时，表示更新记录； 不存在时，表示插入
     *
     * @param req
     * @return
     */
    /**
     * 业务职责：保存文章新增或更新数据，并返回最终文章 ID。
     *
     * 执行流程：转换请求、生成 slug、处理图片与敏感内容，再在同一事务中保存文章、正文、标签和专栏关系。
     */
    @Override
    public Long saveArticle(ArticlePostReq req, Long author) {
        // 将接口请求转换为主表数据，并在保存前生成可用于详情访问的唯一 slug。
        ArticleDO article = ArticleConverter.toArticleDo(req, author);
        article.setUrlSlug(resolveArticleUrlSlug(req));
        // 先替换 Markdown 图片引用，使审核和持久化使用最终正文内容。
        String content = imageService.mdImgReplace(req.getContent());
        if (!canBypassArticlePublishModeration(author)) {
            // 普通作者在主数据落库前记录敏感词命中，为审核和风险追踪提供依据。
            recordSensitiveHits(req.getTitle(), req.getSummary(), content);
        }
        // 主记录、正文、标签及专栏关系需要原子提交，避免留下不完整文章。
        return transactionTemplate.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(TransactionStatus status) {
                Long articleId;
                if (NumUtil.nullOrZero(req.getArticleId())) {
                    // 未携带文章 ID 时创建新文章及其关联数据。
                    articleId = insertArticle(article, content, req.getTagIds());
                    log.info("文章发布成功! title={}", req.getTitle());
                } else {
                    // 携带文章 ID 时更新已有文章及其关联数据。
                    articleId = updateArticle(article, content, req.getTagIds());
                    log.info("文章更新成功！ title={}", article.getTitle());
                }
                if (req.getColumnId() != null) {
                    // 更新文章对应的专栏信息
                    columnSettingService.saveColumnArticle(articleId, req.getColumnId());
                }
                return articleId;
            }
        });
    }

    /**
     * 业务职责：解析文章最终使用的 URL slug，并保证其格式合法且全站唯一。
     *
     * 流程作用：文章新增或编辑时由保存流程调用，为详情页地址和 SEO 标识提供稳定值。
     */
    private String resolveArticleUrlSlug(ArticlePostReq req) {
        // 优先采用用户显式输入的 slug，并统一为小写以避免大小写导致重复地址。
        String inputSlug = StringUtils.trimToNull(req.getUrlSlug());
        //存在输入的slug
        if (inputSlug != null) {
            String normalizedSlug = inputSlug.toLowerCase(Locale.ENGLISH);
            //输入了无效slug
            if (!isValidArticleSlug(normalizedSlug)) {
                throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "文章slug只能包含小写字母、数字和连字符，且不能是纯数字!");
            }
            //重复slug
            if (isArticleSlugOccupied(normalizedSlug, req.getArticleId())) {
                throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "该urlslug已被占用，请修改后重试!");
            }
            return normalizedSlug;
        }

        // 编辑时 slug 留空则沿用历史值，避免无关编辑导致详情地址变化。
        if (!NumUtil.nullOrZero(req.getArticleId())) {
            ArticleDO oldArticle = articleDao.getById(req.getArticleId());
            if (oldArticle != null) {
                return oldArticle.getUrlSlug();
            }
        }

        // 新文章始终使用文章标题生成 slug，避免短标题影响详情地址。
        return generateUniqueArticleSlug(req.getTitle(), req.getArticleId());
    }

    /**
     * 业务职责：在文章与专栏共享的地址空间中生成唯一 slug。
     *
     * 流程作用：避免文章和专栏详情路由冲突；编辑当前文章时排除自身已有 slug。
     */
    private String generateUniqueArticleSlug(String title, Long articleId) {
        String baseSlug;
        try {
            String aiSlug = slugGeneratorService.generateSlugWithAI(title);
            baseSlug = isValidArticleSlug(aiSlug)
                    ? UrlSlugUtil.generateSlug(aiSlug)
                    : UrlSlugUtil.generateSlug(title);
        } catch (Exception e) {
            log.warn("AI生成文章slug失败: title={}", title, e);
            baseSlug = UrlSlugUtil.generateSlug(title);
        }

        // UrlSlugUtil 保证基础 slug 非空、格式合法且长度受控；这里仅保留文章地址空间的唯一化逻辑。
        String slug = baseSlug;
        int suffix = 2;
        // 发现冲突时追加递增后缀，直到文章和专栏均未占用该地址。
        while (isArticleSlugOccupied(slug, articleId)) {
            String suffixText = "-" + suffix++;
            int baseMaxLength = ARTICLE_SLUG_MAX_LENGTH - suffixText.length();
            String prefix = baseSlug.length() > baseMaxLength ? baseSlug.substring(0, baseMaxLength) : baseSlug;
            slug = prefix + suffixText;
        }
        return slug;
    }

    private boolean isArticleSlugOccupied(String urlSlug, Long articleId) {
        return articleDao.existsUrlSlug(urlSlug, articleId) || columnDao.existsUrlSlug(urlSlug, null);
    }

    private boolean isValidArticleSlug(String urlSlug) {
        return StringUtils.isNotBlank(urlSlug) && UrlSlugUtil.isValidSlug(urlSlug) && !StringUtils.isNumeric(urlSlug);
    }

    /**
     * 新建文章
     *
     * @param article
     * @param content
     * @param tags
     * @return
     */
    /**
     * 业务职责：创建文章主记录、正文和标签关系，并发布文章生命周期事件。
     *
     * 流程作用：由保存流程在新文章场景调用，确保关联数据和下游索引、通知处理拥有完整的创建上下文。
     */
    private Long insertArticle(ArticleDO article, String content, Set<Long> tags) {
        // article + article_detail + tag  三张表的数据变更
        // 在线文章由非豁免作者发布时先转入审核状态，防止未审核内容直接展示。
        if (needToReview(article)) {
            // 非白名单中的作者发布文章需要进行审核
            article.setStatus(PushStatusEnum.REVIEW.getCode());
        }

        // 1. 保存文章
        // 使用分布式id生成文章主键
        // 先生成分布式文章 ID，再依次保存主记录、正文和标签关系。
        Long articleId = IdUtil.genId();
        article.setId(articleId);
        articleDao.saveOrUpdate(article);

        // 2. 保存文章内容
        articleDao.saveArticleContent(articleId, content);

        // 3. 保存文章标签
        articleTagDao.batchSave(articleId, tags);

        // 发布文章，阅读计数+1
        userFootService.saveOrUpdateUserFoot(DocumentTypeEnum.ARTICLE, articleId, article.getUserId(), article.getUserId(), OperateTypeEnum.READ);

        // todo 事件发布这里可以进行优化，一次发送多个事件？ 或者借助bit知识点来表示多种事件状态
        // 发布文章创建事件
        // 创建事件驱动搜索、缓存和消息等下游处理，避免写流程与其强耦合。
        SpringUtil.publishEvent(new ArticleMsgEvent<>(this, ArticleEventEnum.CREATE, article));
        // 文章直接上线时，发布上线事件
        if (Objects.equals(article.getStatus(), PushStatusEnum.ONLINE.getCode())) {
            SpringUtil.publishEvent(new ArticleMsgEvent<>(this, ArticleEventEnum.ONLINE, article));
        } else if (Objects.equals(article.getStatus(), PushStatusEnum.REVIEW.getCode())) {
            SpringUtil.publishEvent(new ArticleMsgEvent<>(this, ArticleEventEnum.REVIEW, article));
        }
        return articleId;
    }

    /**
     * 更新文章
     *
     * @param article
     * @param content
     * @param tags
     * @return
     */
    /**
     * 业务职责：更新文章主记录、正文和标签，并按状态发布审核或上线事件。
     *
     * 流程作用：由保存流程在编辑场景调用，保持文章内容、标签、审核状态和下游索引同步。
     */
    private Long updateArticle(ArticleDO article, String content, Set<Long> tags) {
        // fixme 待补充文章的历史版本支持：若文章处于审核状态，则直接更新上一条记录；否则新插入一条记录
        // 记录编辑前是否处于审核状态，正文版本写入策略依赖该状态。
        boolean review = article.getStatus().equals(PushStatusEnum.REVIEW.getCode());
        if (needToReview(article)) {
            article.setStatus(PushStatusEnum.REVIEW.getCode());
        }
        // 更新文章
        article.setUpdateTime(new Date());
        // 先更新主记录，再同步正文和标签，三者由外层事务统一提交。
        articleDao.updateById(article);

        // 更新内容
        articleDao.updateArticleContent(article.getId(), content, review);

        // 标签更新
        if (tags != null && !tags.isEmpty()) {
            articleTagDao.updateTags(article.getId(), tags);
        }

        // 发布文章待审核事件
        if (article.getStatus() == PushStatusEnum.ONLINE.getCode()) {
            // 修改之后依然直接上线 （对于白名单作者而言）
            SpringUtil.publishEvent(new ArticleMsgEvent<>(this, ArticleEventEnum.ONLINE, article));
        } else if (review) {
            // 非白名单作者，修改再审核中的文章，依然是待审核状态
            SpringUtil.publishEvent(new ArticleMsgEvent<>(this, ArticleEventEnum.REVIEW, article));
        }
        return article.getId();
    }


    /**
     * 删除文章
     *
     * @param articleId
     */
    /**
     * 业务职责：校验文章归属后逻辑删除文章，并发布删除事件。
     *
     * 执行流程：查询文章，拒绝非作者删除请求；对未删除文章设置删除标记，再通知下游清理关联数据。
     */
    @Override
    public void deleteArticle(Long articleId, Long loginUserId) {
        // 先校验文章归属，避免已登录用户删除他人的内容。
        ArticleDO dto = articleDao.getById(articleId);
        if (dto != null && !Objects.equals(dto.getUserId(), loginUserId)) {
            // 没有权限
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "请确认文章是否属于您!");
        }

        // 逻辑删除保持数据可追溯；重复删除不重复发布下游事件。
        if (dto != null && dto.getDeleted() != YesOrNoEnum.YES.getCode()) {
            dto.setDeleted(YesOrNoEnum.YES.getCode());
            articleDao.updateById(dto);

            // 发布文章删除事件
            // 删除事件用于同步搜索、缓存等下游系统。
            SpringUtil.publishEvent(new ArticleMsgEvent<>(this, ArticleEventEnum.DELETE, dto));
        }
    }


    /**
     * 非白名单的用户，发布的文章需要先进行审核
     *
     * @param article
     * @return
     */
    /**
     * 业务职责：判断在线文章是否需要在本次保存后进入审核状态。
     *
     * 流程作用：新增和更新流程据此决定是否将文章从直接上线改为待审核，落实普通作者的发布审核规则。
     */
    private boolean needToReview(ArticleDO article) {
        return article.getStatus() == PushStatusEnum.ONLINE.getCode() && !canBypassArticlePublishModeration(article.getUserId());
    }

    /**
     * 业务职责：检测文章标题、摘要和正文中的敏感内容。
     *
     * 流程作用：该方法在文章保存前执行；敏感服务负责记录或处理命中结果，为普通作者的发布审核提供依据。
     */
    private void recordSensitiveHits(String title, String summary, String content) {
        // 使用换行拼接各展示字段，既保留字段边界，也避免遗漏标题或摘要中的敏感内容。
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, title);
        appendIfPresent(builder, summary);
        appendIfPresent(builder, content);
        // 无可检测文本时不调用敏感服务，避免无意义的审核操作。
        if (builder.length() > 0) {
            sensitiveService.contains(builder.toString());
        }
    }

    /**
     * 业务职责：将非空文章字段追加到敏感检测文本。
     *
     * 流程作用：为敏感内容检测构建完整输入，并以换行分隔字段以保留内容边界。
     */
    private void appendIfPresent(StringBuilder builder, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text);
    }

    /**
     * 管理员、运营和文章发布白名单作者，统一豁免发布审核与敏感词拦截。
     *
     * @param authorId 作者id
     * @return true 表示可直接发布
     */
    /**
     * 业务职责：判断作者是否具备跳过文章发布审核和敏感内容检测的资格。
     *
     * 流程作用：文章保存流程依据该结果决定是否执行敏感检测，以及在线文章是否需要转为待审核。
     */
    private boolean canBypassArticlePublishModeration(Long authorId) {
        // 优先使用请求上下文中的角色，管理员可直接跳过发布审核。
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        BaseUserInfoDTO user = reqInfo == null ? null : reqInfo.getUser();
        if (user != null && UserRole.hasAdminPermission(user.getRole())) {
            return true;
        }
        // 非管理员仅在作者白名单中时具备豁免资格；空作者不会命中白名单。
        return authorId != null && articleWhiteListService.authorInArticleWhiteList(authorId);
    }
}
