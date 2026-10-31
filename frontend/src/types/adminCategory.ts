export interface AdminCategoryDto {
  id: string;
  name: string;
  nameEn: string | null;
  slug: string;
  parentId: string | null;
  iconUrl: string | null;
  isActive: boolean;
}

export interface AdminCategoryTreeDto extends AdminCategoryDto {
  children: AdminCategoryTreeDto[];
}

export interface AdminCategoryResponse extends AdminCategoryDto {
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CreateAdminCategoryRequest {
  name: string;
  nameEn?: string;
  slug: string;
  parentId?: string | null;
  iconUrl?: string;
}

export interface UpdateAdminCategoryRequest {
  name?: string;
  nameEn?: string;
  slug?: string;
  // Backend treats null/absent as unchanged; it cannot detach a child to a root.
  parentId?: string;
  iconUrl?: string;
  isActive?: boolean;
}
