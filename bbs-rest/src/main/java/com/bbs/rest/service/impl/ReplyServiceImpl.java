package com.bbs.rest.service.impl;

import com.bbs.common.base.BaseServiceImpl;
import com.bbs.common.dao.PostsDao;
import com.bbs.common.dao.ReplyDao;
import com.bbs.common.entity.Notification;
import com.bbs.common.entity.Posts;
import com.bbs.common.entity.Reply;
import com.bbs.common.entity.User;
import com.bbs.common.exception.ServiceProcessException;
import com.bbs.rest.service.NotificationService;
import com.bbs.rest.service.ReplyService;
import com.bbs.rest.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.*;
import java.util.Date;

@Service
public class ReplyServiceImpl extends BaseServiceImpl<ReplyDao, Reply> implements ReplyService {
    @Autowired
    private PostsDao postsDao;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private WebSocketService webSocketService;

    @Override
    public Page<Reply> getReplyByPage(final Integer postsId, int pageNo, int length) {
        Sort.Order order = new Sort.Order(Sort.Direction.ASC, "id");
        Sort sort = new Sort(order);
        PageRequest pageable = new PageRequest(pageNo, length, sort);

        Specification<Reply> specification = new Specification<Reply>() {

            @Override
            public Predicate toPredicate(Root<Reply> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
                Path<Integer> $posts = root.get("posts");
                Predicate predicate = criteriaBuilder.and(criteriaBuilder.equal($posts, postsId));
                return predicate;
            }
        };
        Page<Reply> page = repository.findAll(specification, pageable);
        return page;
    }


    @Transactional
    @Override
    public void saveReply(Reply reply, Integer postsId, User user) {
        try {
            Posts posts = postsDao.findOne(postsId);

            if (posts == null) throw new ServiceProcessException("帖子不存在!");

            //帖子回复数+1
            int count = posts.getReplyCount();
            posts.setReplyCount(++count);
            postsDao.save(posts);

            //添加回复
            reply.setInitTime(new Date());
            reply.setUser(user);
            reply.setPosts(posts);
            repository.save(reply);

            //判断是否是自问自回，如果是则不通知
            if (posts.getUser().getId()!=user.getId()) {
                //向消息表中增加信息
                Notification notification = new Notification();
                notification.setPosts(posts);
                notification.setFromuser(user);
                notification.setTouser(posts.getUser());
                notification.setInitTime(new Date());
                notificationService.save(notification);
                // 使用WebSocket进行1对1通知
                webSocketService.sendToOne(posts.getUser().getId());
            }
        } catch (ServiceProcessException e) {
            throw e;
        } catch (Exception e) {
            // 所有编译期异常转换为运行期异常
            throw new ServiceProcessException("发布回复失败!");
        }
    }
}
