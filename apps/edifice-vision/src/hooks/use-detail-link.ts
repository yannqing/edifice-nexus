"use client";

import { useEffect, useRef } from "react";

export function useDetailLink(openDetail: (id: string) => void, openApproval?: (id: string) => void) {
  const openDetailRef = useRef(openDetail);
  const openApprovalRef = useRef(openApproval);

  useEffect(() => {
    openDetailRef.current = openDetail;
  }, [openDetail]);

  useEffect(() => {
    openApprovalRef.current = openApproval;
  }, [openApproval]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const detailId = params.get("detailId");
    if (!detailId || !/^\d+$/.test(detailId)) return;

    const timer = window.setTimeout(() => {
      if (params.get("action") === "approve" && openApprovalRef.current) {
        openApprovalRef.current(detailId);
      } else {
        openDetailRef.current(detailId);
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);
}
