package com.qsy.edifice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectFileFolder;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectFileFolderMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectFileFolderService {
    private static final int MAX_DEPTH = 20;
    private static final int MAX_FOLDERS = 5000;
    @Resource private ProjectFileFolderMapper folderMapper;
    @Resource private ProjectMapper projectMapper;
    @Resource private ProjectMemberService projectMemberService;

    public List<ProjectFileFolder> list(Long projectId, Long userId) {
        authorize(projectId == null ? null : projectMapper.selectById(projectId), userId, false);
        return folderMapper.selectList(new LambdaQueryWrapper<ProjectFileFolder>()
                .eq(ProjectFileFolder::getProjectId, projectId).orderByAsc(ProjectFileFolder::getFolderName)
                .orderByAsc(ProjectFileFolder::getFolderId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectFileFolder create(Long projectId, Long parentId, String name, Long userId) {
        lockProject(projectId, userId);
        Long parent = validateFolder(projectId, parentId);
        if (depth(projectId, parent) >= MAX_DEPTH) throw new BusinessException("文件夹最多支持20层");
        return createChild(projectId, parent, name(name), userId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> resolvePaths(Long projectId, Long parentId, List<String> paths, Long userId) {
        if (paths == null || paths.isEmpty() || paths.size() > 200 || paths.stream().anyMatch(Objects::isNull))
            throw new BusinessException("每次最多处理200条文件夹路径");
        lockProject(projectId, userId);
        Long base = validateFolder(projectId, parentId);
        int baseDepth = depth(projectId, base);
        Map<String, List<String>> validated = new LinkedHashMap<>();
        for (String path : paths) {
            if (path.isBlank() || path.length() > 2048) throw new BusinessException("文件夹路径不能为空且不能超过2048个字符");
            List<String> parts = Arrays.stream(path.split("/", -1)).map(this::name).toList();
            if (baseDepth + parts.size() > MAX_DEPTH) throw new BusinessException("文件夹最多支持20层");
            validated.put(path, parts);
        }
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Long> resolved = new HashMap<>();
        for (var entry : validated.entrySet()) {
            Long parent = base;
            for (String part : entry.getValue()) {
                String key = (parent == null ? 0 : parent) + "/" + part;
                Long id = resolved.get(key);
                if (id == null) {
                    id = createChild(projectId, parent, part, userId, true).getFolderId();
                    resolved.put(key, id);
                }
                parent = id;
            }
            result.put(entry.getKey(), String.valueOf(parent));
        }
        return result;
    }

    public Long validateFolder(Long projectId, Long folderId) {
        if (folderId == null || folderId == 0) return null;
        ProjectFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null || !Objects.equals(folder.getProjectId(), projectId))
            throw new BusinessException("所选文件夹不属于当前项目或已不存在");
        return folderId;
    }

    public Map<Long, String> pathsFor(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ProjectFileFolder> folders = new HashMap<>();
        Set<Long> frontier = new HashSet<>(ids);
        // Fetch only requested directories and ancestors, not every directory in all listed projects.
        for (int level = 0; level < MAX_DEPTH && !frontier.isEmpty(); level++) {
            List<ProjectFileFolder> found = folderMapper.selectBatchIds(frontier);
            found.forEach(folder -> folders.put(folder.getFolderId(), folder));
            frontier = found.stream().map(ProjectFileFolder::getParentFolderId)
                    .filter(parent -> parent != null && parent != 0 && !folders.containsKey(parent)).collect(Collectors.toSet());
        }
        Map<Long, String> result = new HashMap<>();
        for (Long id : ids) {
            List<String> parts = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            Long cursor = id;
            Long projectId = folders.containsKey(id) ? folders.get(id).getProjectId() : null;
            while (cursor != null && cursor != 0) {
                ProjectFileFolder folder = folders.get(cursor);
                if (folder == null || !Objects.equals(folder.getProjectId(), projectId) || !seen.add(cursor) || seen.size() > MAX_DEPTH)
                    throw new BusinessException("文件夹结构异常，请联系管理员");
                parts.add(folder.getFolderName()); cursor = folder.getParentFolderId();
            }
            Collections.reverse(parts);
            result.put(id, String.join("/", parts));
        }
        return result;
    }

    private void lockProject(Long projectId, Long userId) {
        authorize(projectId == null ? null : projectMapper.selectByIdForUpdate(projectId), userId, true);
    }

    private void authorize(Project project, Long userId, boolean write) {
        if (project == null) throw new BusinessException("项目不存在");
        if (userId == null) throw new BusinessException(ErrorType.NO_AUTH_ERROR);
        if (write && Objects.equals(project.getArchiveStatus(), 1)) throw new BusinessException("项目已归档，不能创建文件夹");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean global = auth != null && auth.getAuthorities().stream().anyMatch(a -> Set.of(
                "menu:all-projects", "menu:project-files-approval", "ROLE_SUPER_ADMIN").contains(a.getAuthority()));
        if (!global && projectMemberService.getProjectMemberByProjectIdAndUserId(project.getProjectId(), userId) == null)
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只有当前项目成员或文件管理员可以访问文件夹");
    }

    private int depth(Long projectId, Long folderId) {
        int count = 0;
        Set<Long> seen = new HashSet<>();
        while (folderId != null && folderId != 0) {
            validateFolder(projectId, folderId);
            if (!seen.add(folderId) || ++count > MAX_DEPTH) throw new BusinessException("文件夹结构异常");
            folderId = folderMapper.selectById(folderId).getParentFolderId();
        }
        return count;
    }

    private String name(String value) {
        if (value != null && value.chars().anyMatch(c -> c < 32 || c == 127))
            throw new BusinessException("文件夹名称不能包含控制字符");
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > 128 || text.equals(".") || text.equals("..")
                || text.chars().anyMatch(c -> c < 32 || c == 127 || "\\/:*?\"<>|".indexOf(c) >= 0))
            throw new BusinessException("文件夹名称须为1到128个字符，不能包含路径分隔符、特殊字符或相对路径");
        return text;
    }

    private ProjectFileFolder createChild(Long projectId, Long parentId, String name, Long userId, boolean reuse) {
        long parent = parentId == null ? 0 : parentId;
        var query = new LambdaQueryWrapper<ProjectFileFolder>().eq(ProjectFileFolder::getProjectId, projectId)
                .eq(ProjectFileFolder::getParentFolderId, parent).eq(ProjectFileFolder::getFolderName, name);
        ProjectFileFolder existing = folderMapper.selectOne(query);
        if (existing != null) {
            if (reuse) return existing;
            throw new BusinessException("同一目录下已存在同名文件夹");
        }
        if (folderMapper.selectCount(new LambdaQueryWrapper<ProjectFileFolder>()
                .eq(ProjectFileFolder::getProjectId, projectId)) >= MAX_FOLDERS) throw new BusinessException("每个项目最多5000个文件夹");
        ProjectFileFolder folder = ProjectFileFolder.builder().projectId(projectId).parentFolderId(parent)
                .folderName(name).createdBy(userId).build();
        folderMapper.insert(folder);
        return folder;
    }
}
