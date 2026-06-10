package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.dto.AdminDto;
import com.questforge.entity.SysSubject;
import com.questforge.mapper.SysSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 真实有效的知识域(科目)分类服务
 */
@Service
@RequiredArgsConstructor
public class SysSubjectService {

    private final SysSubjectMapper sysSubjectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateSubject(AdminDto.SubjectReq req) {
        SysSubject subject = new SysSubject();
        subject.setId(req.getId());
        subject.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        subject.setName(req.getName());

        if (subject.getId() == null) {
            sysSubjectMapper.insert(subject);
        } else {
            sysSubjectMapper.updateById(subject);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSubject(Long id) {
        // 校验防误删：若该科目下存在子节点，拒绝直接删除
        Long childrenCount = sysSubjectMapper.selectCount(
                new LambdaQueryWrapper<SysSubject>().eq(SysSubject::getParentId, id)
        );
        if (childrenCount > 0) {
            throw new RuntimeException("操作失败：该知识域下包含子节点，请先删除子节点！");
        }
        sysSubjectMapper.deleteById(id);
    }

    /**
     * 递归构建无限极级联的知识域树状结构
     */
    public List<AdminDto.SubjectTreeResp> getSubjectTree() {
        List<SysSubject> allSubjects = sysSubjectMapper.selectList(
                new LambdaQueryWrapper<SysSubject>().orderByAsc(SysSubject::getId)
        );

        return allSubjects.stream()
                .filter(s -> s.getParentId() == 0L) // 从顶级节点开始
                .map(s -> convertToTreeNode(s, allSubjects))
                .collect(Collectors.toList());
    }

    private AdminDto.SubjectTreeResp convertToTreeNode(SysSubject subject, List<SysSubject> allSubjects) {
        AdminDto.SubjectTreeResp node = new AdminDto.SubjectTreeResp();
        node.setId(subject.getId());
        node.setParentId(subject.getParentId());
        node.setName(subject.getName());

        // 递归装载子节点
        List<AdminDto.SubjectTreeResp> children = allSubjects.stream()
                .filter(s -> s.getParentId().equals(subject.getId()))
                .map(s -> convertToTreeNode(s, allSubjects))
                .collect(Collectors.toList());

        node.setChildren(children);
        return node;
    }
}