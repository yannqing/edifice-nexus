"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Pencil, Trash2, Tag, Layers } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import {
  getProjectTypeList,
  createProjectType,
  updateProjectType,
  deleteProjectType,
} from "@/services/project-type";
import type { ProjectTypeVo } from "@/services/project-type";
import {
  getStageTemplateList,
  createStageTemplate,
  updateStageTemplate,
  deleteStageTemplate,
} from "@/services/stage-template";
import type { StageTemplateVo } from "@/services/stage-template";
import { getAllProjectTypes } from "@/services/project-type";
import { ResponseCode } from "@/types/api";

type TabKey = "type" | "stage";
const PAGE_SIZE = 10;

const tabs: { key: TabKey; label: string; icon: typeof Tag }[] = [
  { key: "type", label: "项目类型", icon: Tag },
  { key: "stage", label: "阶段模板", icon: Layers },
];

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

export default function ProjectConfigPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("type");

  // ========== 项目类型状态 ==========
  const [typeItems, setTypeItems] = useState<ProjectTypeVo[]>([]);
  const [typeTotal, setTypeTotal] = useState(0);
  const [typePage, setTypePage] = useState(1);
  const [typeLoading, setTypeLoading] = useState(true);
  const [typeStatusFilter, setTypeStatusFilter] = useState<string>("all");

  const [typeFormOpen, setTypeFormOpen] = useState(false);
  const [typeEditing, setTypeEditing] = useState<ProjectTypeVo | null>(null);
  const [typeFormCode, setTypeFormCode] = useState("");
  const [typeFormName, setTypeFormName] = useState("");
  const [typeFormStatus, setTypeFormStatus] = useState(1);
  const [typeSubmitting, setTypeSubmitting] = useState(false);
  const [typeDeleteId, setTypeDeleteId] = useState<string | null>(null);

  // ========== 阶段模板状态 ==========
  const [stageItems, setStageItems] = useState<StageTemplateVo[]>([]);
  const [stageTotal, setStageTotal] = useState(0);
  const [stagePage, setStagePage] = useState(1);
  const [stageLoading, setStageLoading] = useState(true);
  const [stageTypeFilter, setStageTypeFilter] = useState<string>("all");
  const [stageStatusFilter, setStageStatusFilter] = useState<string>("all");
  const [projectTypes, setProjectTypes] = useState<ProjectTypeVo[]>([]);

  const [stageFormOpen, setStageFormOpen] = useState(false);
  const [stageEditing, setStageEditing] = useState<StageTemplateVo | null>(null);
  const [stageFormName, setStageFormName] = useState("");
  const [stageFormTypeId, setStageFormTypeId] = useState("");
  const [stageFormOutput, setStageFormOutput] = useState("");
  const [stageFormStatus, setStageFormStatus] = useState(1);
  const [stageSubmitting, setStageSubmitting] = useState(false);
  const [stageDeleteId, setStageDeleteId] = useState<string | null>(null);

  // ========== 项目类型逻辑 ==========
  const fetchTypeList = useCallback(
    async (signal?: AbortSignal) => {
      setTypeLoading(true);
      try {
        const res = await getProjectTypeList(
          {
            status: typeStatusFilter === "all" ? undefined : Number(typeStatusFilter),
            current: typePage,
            pageSize: PAGE_SIZE,
          },
          signal
        );
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setTypeItems(res.data.records ?? []);
          setTypeTotal(res.data.total ?? 0);
        }
      } catch (err) {
        if (!isAbortError(err)) { setTypeItems([]); setTypeTotal(0); }
      } finally {
        setTypeLoading(false);
      }
    },
    [typeStatusFilter, typePage]
  );

  useEffect(() => { setTypePage(1); }, [typeStatusFilter]);

  useEffect(() => {
    if (activeTab !== "type") return;
    const c = new AbortController();
    fetchTypeList(c.signal);
    return () => c.abort();
  }, [fetchTypeList, activeTab]);

  const openTypeCreate = () => {
    setTypeEditing(null);
    setTypeFormCode("");
    setTypeFormName("");
    setTypeFormStatus(1);
    setTypeFormOpen(true);
  };

  const openTypeEdit = (item: ProjectTypeVo) => {
    setTypeEditing(item);
    setTypeFormCode(item.projectTypeCode);
    setTypeFormName(item.projectTypeName);
    setTypeFormStatus(item.projectTypeStatus);
    setTypeFormOpen(true);
  };

  const handleTypeSubmit = async () => {
    if (!typeFormCode.trim() || !typeFormName.trim()) { toast.error("编码和名称不能为空"); return; }
    setTypeSubmitting(true);
    try {
      const payload = { projectTypeCode: typeFormCode.trim().toUpperCase(), projectTypeName: typeFormName.trim(), projectTypeStatus: typeFormStatus };
      const res = typeEditing
        ? await updateProjectType({ ...payload, projectTypeId: typeEditing.projectTypeId })
        : await createProjectType(payload);
      if (res.code === ResponseCode.SUCCESS) { toast.success(typeEditing ? "修改成功" : "创建成功"); setTypeFormOpen(false); fetchTypeList(); }
      else toast.error(res.msg || "操作失败");
    } catch { toast.error("操作失败"); } finally { setTypeSubmitting(false); }
  };

  const handleTypeDelete = async () => {
    if (!typeDeleteId) return;
    try {
      const res = await deleteProjectType(typeDeleteId);
      if (res.code === ResponseCode.SUCCESS) { toast.success("删除成功"); setTypeDeleteId(null); fetchTypeList(); }
      else toast.error(res.msg || "删除失败");
    } catch { toast.error("删除失败"); }
  };

  // ========== 阶段模板逻辑 ==========
  useEffect(() => {
    getAllProjectTypes().then((res) => { if (res.code === ResponseCode.SUCCESS) setProjectTypes(res.data ?? []); });
  }, []);

  const fetchStageList = useCallback(
    async (signal?: AbortSignal) => {
      setStageLoading(true);
      try {
        const res = await getStageTemplateList(
          {
            projectTypeId: stageTypeFilter === "all" ? undefined : stageTypeFilter,
            status: stageStatusFilter === "all" ? undefined : Number(stageStatusFilter),
            current: stagePage,
            pageSize: PAGE_SIZE,
          },
          signal
        );
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setStageItems(res.data.records ?? []);
          setStageTotal(res.data.total ?? 0);
        }
      } catch (err) {
        if (!isAbortError(err)) { setStageItems([]); setStageTotal(0); }
      } finally {
        setStageLoading(false);
      }
    },
    [stageTypeFilter, stageStatusFilter, stagePage]
  );

  useEffect(() => { setStagePage(1); }, [stageTypeFilter, stageStatusFilter]);

  useEffect(() => {
    if (activeTab !== "stage") return;
    const c = new AbortController();
    fetchStageList(c.signal);
    return () => c.abort();
  }, [fetchStageList, activeTab]);

  const getTypeName = (typeId: string) =>
    projectTypes.find((t) => t.projectTypeId === typeId)?.projectTypeName ?? typeId;

  const openStageCreate = () => {
    setStageEditing(null);
    setStageFormName("");
    setStageFormTypeId(projectTypes.length > 0 ? projectTypes[0].projectTypeId : "");
    setStageFormOutput("");
    setStageFormStatus(1);
    setStageFormOpen(true);
  };

  const openStageEdit = (item: StageTemplateVo) => {
    setStageEditing(item);
    setStageFormName(item.stageName);
    setStageFormTypeId(item.projectTypeId);
    setStageFormOutput(String(item.stageOutput));
    setStageFormStatus(item.stageStatus);
    setStageFormOpen(true);
  };

  const handleStageSubmit = async () => {
    if (!stageFormName.trim() || !stageFormTypeId) { toast.error("阶段名称和项目类型不能为空"); return; }
    const output = Number(stageFormOutput);
    if (isNaN(output) || output < 0 || output > 100) { toast.error("产值比例需在 0-100 之间"); return; }
    setStageSubmitting(true);
    try {
      const payload = { stageName: stageFormName.trim(), projectTypeId: stageFormTypeId, stageOutput: output, stageStatus: stageFormStatus };
      const res = stageEditing
        ? await updateStageTemplate({ ...payload, stageId: stageEditing.stageId })
        : await createStageTemplate(payload);
      if (res.code === ResponseCode.SUCCESS) { toast.success(stageEditing ? "修改成功" : "创建成功"); setStageFormOpen(false); fetchStageList(); }
      else toast.error(res.msg || "操作失败");
    } catch { toast.error("操作失败"); } finally { setStageSubmitting(false); }
  };

  const handleStageDelete = async () => {
    if (!stageDeleteId) return;
    try {
      const res = await deleteStageTemplate(stageDeleteId);
      if (res.code === ResponseCode.SUCCESS) { toast.success("删除成功"); setStageDeleteId(null); fetchStageList(); }
      else toast.error(res.msg || "删除失败");
    } catch { toast.error("删除失败"); }
  };

  const typeTotalPages = Math.ceil(typeTotal / PAGE_SIZE);
  const stageTotalPages = Math.ceil(stageTotal / PAGE_SIZE);

  return (
    <div className="p-4 md:p-8 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">项目配置</h1>
        <p className="text-slate-500 text-sm mt-1">管理项目类型与阶段模板，配置后新建项目时自动生效。</p>
      </div>

      {/* Tabs */}
      <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100 w-fit">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setActiveTab(t.key)}
            className={cn(
              "px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
              activeTab === t.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700"
            )}
          >
            <t.icon className="w-4 h-4" /> {t.label}
          </button>
        ))}
      </div>

      {/* ==================== 项目类型 Tab ==================== */}
      {activeTab === "type" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <select
              value={typeStatusFilter}
              onChange={(e) => setTypeStatusFilter(e.target.value)}
              className="px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">全部状态</option>
              <option value="1">启用</option>
              <option value="0">禁用</option>
            </select>
            <Button onClick={openTypeCreate} className="gap-2">
              <Plus className="w-4 h-4" /> 新建类型
            </Button>
          </div>

          {typeLoading && <TablePageSkeleton columns={5} rows={5} />}

          {!typeLoading && typeItems.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <Tag className="w-12 h-12 mb-3" />
              <p className="text-sm">暂无项目类型数据</p>
            </div>
          )}

          {!typeLoading && typeItems.length > 0 && (
            <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50/50">
                  <tr className="text-slate-500 text-xs uppercase tracking-wider">
                    <th className="text-left py-4 px-6 font-semibold">类型编码</th>
                    <th className="text-left py-4 px-4 font-semibold">类型名称</th>
                    <th className="text-center py-4 px-4 font-semibold">状态</th>
                    <th className="text-left py-4 px-4 font-semibold">创建时间</th>
                    <th className="text-right py-4 px-6 font-semibold">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {typeItems.map((item) => (
                    <tr key={item.projectTypeId} className="hover:bg-slate-50/50">
                      <td className="py-3 px-6">
                        <span className="inline-flex items-center gap-1.5 font-mono font-medium text-slate-800">
                          <span className={cn("w-2 h-2 rounded-full", item.projectTypeStatus === 1 ? "bg-emerald-500" : "bg-slate-300")} />
                          {item.projectTypeCode}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-medium text-slate-800">{item.projectTypeName}</td>
                      <td className="py-3 px-4 text-center">
                        <Badge variant={item.projectTypeStatus === 1 ? "default" : "secondary"}>
                          {item.projectTypeStatus === 1 ? "启用" : "禁用"}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 text-slate-500">{formatTime(item.createdTime)}</td>
                      <td className="py-3 px-6 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button onClick={() => openTypeEdit(item)} className="p-2 rounded-lg text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-colors" title="编辑">
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button onClick={() => setTypeDeleteId(item.projectTypeId)} className="p-2 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors" title="删除">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {typeTotalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>共 {typeTotal} 条</span>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" disabled={typePage <= 1} onClick={() => setTypePage((p) => p - 1)}>上一页</Button>
                <span>{typePage} / {typeTotalPages}</span>
                <Button variant="outline" size="sm" disabled={typePage >= typeTotalPages} onClick={() => setTypePage((p) => p + 1)}>下一页</Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ==================== 阶段模板 Tab ==================== */}
      {activeTab === "stage" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div className="flex gap-3">
              <select
                value={stageTypeFilter}
                onChange={(e) => setStageTypeFilter(e.target.value)}
                className="px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="all">全部项目类型</option>
                {projectTypes.map((t) => (
                  <option key={t.projectTypeId} value={t.projectTypeId}>{t.projectTypeCode} - {t.projectTypeName}</option>
                ))}
              </select>
              <select
                value={stageStatusFilter}
                onChange={(e) => setStageStatusFilter(e.target.value)}
                className="px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="all">全部状态</option>
                <option value="1">启用</option>
                <option value="0">禁用</option>
              </select>
            </div>
            <Button onClick={openStageCreate} className="gap-2">
              <Plus className="w-4 h-4" /> 新建模板
            </Button>
          </div>

          {stageLoading && <TablePageSkeleton columns={5} rows={5} />}

          {!stageLoading && stageItems.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <Layers className="w-12 h-12 mb-3" />
              <p className="text-sm">暂无阶段模板数据</p>
            </div>
          )}

          {!stageLoading && stageItems.length > 0 && (
            <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50/50">
                  <tr className="text-slate-500 text-xs uppercase tracking-wider">
                    <th className="text-left py-4 px-6 font-semibold">阶段名称</th>
                    <th className="text-left py-4 px-4 font-semibold">所属项目类型</th>
                    <th className="text-right py-4 px-4 font-semibold">产值比例</th>
                    <th className="text-center py-4 px-4 font-semibold">状态</th>
                    <th className="text-right py-4 px-6 font-semibold">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {stageItems.map((item) => (
                    <tr key={item.stageId} className="hover:bg-slate-50/50">
                      <td className="py-3 px-6 font-medium text-slate-800">{item.stageName}</td>
                      <td className="py-3 px-4 text-slate-600">{getTypeName(item.projectTypeId)}</td>
                      <td className="py-3 px-4 text-right font-mono text-slate-700">{item.stageOutput}%</td>
                      <td className="py-3 px-4 text-center">
                        <Badge variant={item.stageStatus === 1 ? "default" : "secondary"}>
                          {item.stageStatus === 1 ? "启用" : "禁用"}
                        </Badge>
                      </td>
                      <td className="py-3 px-6 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button onClick={() => openStageEdit(item)} className="p-2 rounded-lg text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-colors" title="编辑">
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button onClick={() => setStageDeleteId(item.stageId)} className="p-2 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors" title="删除">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {stageTotalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>共 {stageTotal} 条</span>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" disabled={stagePage <= 1} onClick={() => setStagePage((p) => p - 1)}>上一页</Button>
                <span>{stagePage} / {stageTotalPages}</span>
                <Button variant="outline" size="sm" disabled={stagePage >= stageTotalPages} onClick={() => setStagePage((p) => p + 1)}>下一页</Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ==================== 项目类型弹窗 ==================== */}
      {typeFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6 space-y-5">
            <h2 className="text-lg font-bold text-slate-900">{typeEditing ? "编辑项目类型" : "新建项目类型"}</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">类型编码 <span className="text-red-500">*</span></label>
                <input type="text" value={typeFormCode} onChange={(e) => setTypeFormCode(e.target.value)} placeholder="如 A、B、C"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">类型名称 <span className="text-red-500">*</span></label>
                <input type="text" value={typeFormName} onChange={(e) => setTypeFormName(e.target.value)} placeholder="如 全程结算、全过程"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">状态</label>
                <div className="flex items-center gap-3">
                  <button type="button" onClick={() => setTypeFormStatus(typeFormStatus === 1 ? 0 : 1)}
                    className={cn("relative inline-flex h-6 w-11 items-center rounded-full transition-colors", typeFormStatus === 1 ? "bg-blue-600" : "bg-slate-300")}>
                    <span className={cn("inline-block h-4 w-4 transform rounded-full bg-white transition-transform", typeFormStatus === 1 ? "translate-x-6" : "translate-x-1")} />
                  </button>
                  <span className="text-sm text-slate-600">{typeFormStatus === 1 ? "启用" : "禁用"}</span>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <Button variant="outline" onClick={() => setTypeFormOpen(false)} disabled={typeSubmitting}>取消</Button>
              <Button onClick={handleTypeSubmit} disabled={typeSubmitting}>{typeSubmitting ? "提交中..." : typeEditing ? "保存" : "创建"}</Button>
            </div>
          </div>
        </div>
      )}

      {/* ==================== 阶段模板弹窗 ==================== */}
      {stageFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6 space-y-5">
            <h2 className="text-lg font-bold text-slate-900">{stageEditing ? "编辑阶段模板" : "新建阶段模板"}</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">所属项目类型 <span className="text-red-500">*</span></label>
                <select value={stageFormTypeId} onChange={(e) => setStageFormTypeId(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="" disabled>请选择项目类型</option>
                  {projectTypes.map((t) => (<option key={t.projectTypeId} value={t.projectTypeId}>{t.projectTypeCode} - {t.projectTypeName}</option>))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">阶段名称 <span className="text-red-500">*</span></label>
                <input type="text" value={stageFormName} onChange={(e) => setStageFormName(e.target.value)} placeholder="如 方案计划编制、初稿编制"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">产值比例 (%) <span className="text-red-500">*</span></label>
                <input type="number" min="0" max="100" step="0.1" value={stageFormOutput} onChange={(e) => setStageFormOutput(e.target.value)} placeholder="0-100"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">状态</label>
                <div className="flex items-center gap-3">
                  <button type="button" onClick={() => setStageFormStatus(stageFormStatus === 1 ? 0 : 1)}
                    className={cn("relative inline-flex h-6 w-11 items-center rounded-full transition-colors", stageFormStatus === 1 ? "bg-blue-600" : "bg-slate-300")}>
                    <span className={cn("inline-block h-4 w-4 transform rounded-full bg-white transition-transform", stageFormStatus === 1 ? "translate-x-6" : "translate-x-1")} />
                  </button>
                  <span className="text-sm text-slate-600">{stageFormStatus === 1 ? "启用" : "禁用"}</span>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <Button variant="outline" onClick={() => setStageFormOpen(false)} disabled={stageSubmitting}>取消</Button>
              <Button onClick={handleStageSubmit} disabled={stageSubmitting}>{stageSubmitting ? "提交中..." : stageEditing ? "保存" : "创建"}</Button>
            </div>
          </div>
        </div>
      )}

      {/* ==================== 删除确认弹窗 ==================== */}
      {(typeDeleteId || stageDeleteId) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-6 space-y-4">
            <h2 className="text-lg font-bold text-slate-900">确认删除</h2>
            <p className="text-sm text-slate-500">删除后不可恢复，确定要删除吗？</p>
            <div className="flex justify-end gap-3">
              <Button variant="outline" onClick={() => { setTypeDeleteId(null); setStageDeleteId(null); }}>取消</Button>
              <Button variant="destructive" onClick={() => { if (typeDeleteId) handleTypeDelete(); else handleStageDelete(); }}>删除</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
