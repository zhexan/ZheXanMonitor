package org.example.entity;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RuntimeDetail {
   long timestamp;
   double cupUsage;
   double memoryUsage;
   double diskUsage;
   double networkUpload;
   double networkDownload;
   double diskRead;
   double disKWrite;
}
