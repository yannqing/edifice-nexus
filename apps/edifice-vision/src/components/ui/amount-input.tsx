"use client";

import { useState, useEffect, useCallback } from "react";
import { cn } from "@/lib/utils";

/**
 * 金额输入组件：用户以「万元」为单位输入，底层以「元」存储。
 *
 * - value 传入的是元（number | undefined）
 * - 展示和输入时自动换算为万元（÷10000）
 * - onChange 回调传出的是元（×10000）
 * - 支持小数输入（最多 4 位，对应元精度），如 12.5 万元 = 125000 元
 */
export function AmountInput({
  value,
  onChange,
  placeholder = "请输入金额",
  className,
  allowDecimal = true,
}: {
  value: number | undefined;
  onChange: (yuan: number | undefined) => void;
  placeholder?: string;
  className?: string;
  allowDecimal?: boolean;
}) {
  // 显示用的万元字符串
  const [display, setDisplay] = useState("");

  // 同步外部 value → 显示值
  const syncFromValue = useCallback((v: number | undefined) => {
    if (v === undefined || v === null || isNaN(v)) {
      setDisplay("");
    } else {
      const wan = v / 10000;
      // 整数万元直接显示，否则保留小数
      setDisplay(Number.isInteger(wan) ? String(wan) : String(parseFloat(wan.toFixed(4))));
    }
  }, []);

  useEffect(() => {
    syncFromValue(value);
  }, [value, syncFromValue]);

  const handleChange = (raw: string) => {
    // 只允许数字和小数点
    const filtered = allowDecimal
      ? raw.replace(/[^\d.]/g, "")
      : raw.replace(/[^\d]/g, "");

    // 防止多个小数点
    const parts = filtered.split(".");
    const cleaned = parts.length > 2
      ? parts[0] + "." + parts.slice(1).join("")
      : filtered;

    setDisplay(cleaned);

    if (cleaned === "" || cleaned === ".") {
      onChange(undefined);
      return;
    }

    const wan = Number(cleaned);
    if (isNaN(wan)) {
      onChange(undefined);
    } else {
      onChange(Math.round(wan * 10000));
    }
  };

  // 失焦时格式化显示
  const handleBlur = () => {
    if (display === "" || display === ".") return;
    const wan = Number(display);
    if (!isNaN(wan)) {
      setDisplay(Number.isInteger(wan) ? String(wan) : String(parseFloat(wan.toFixed(4))));
    }
  };

  return (
    <div className="relative">
      <input
        type="text"
        inputMode="decimal"
        value={display}
        onChange={(e) => handleChange(e.target.value)}
        onBlur={handleBlur}
        placeholder={placeholder}
        className={cn("form-input pr-12", className)}
      />
      <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400 pointer-events-none">
        万元
      </span>
    </div>
  );
}
