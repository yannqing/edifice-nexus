import { get, post } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  ContractBenefitRevisionVo,
  ReviseBenefitParams,
} from "@/types/contract-benefit";

export async function reviseBenefit(
  contractId: string,
  params: ReviseBenefitParams,
): Promise<BaseResponse<number>> {
  return post<number>(`/contract/${contractId}/benefit-revision`, { body: params });
}

export async function getBenefitHistory(
  contractId: string,
): Promise<BaseResponse<ContractBenefitRevisionVo[]>> {
  return get<ContractBenefitRevisionVo[]>(`/contract/${contractId}/benefit-history`);
}
