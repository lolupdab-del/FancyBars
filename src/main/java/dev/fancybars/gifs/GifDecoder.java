package dev.fancybars.gifs;

// Ported from Inventory GIFs by lolupdab (MIT).

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GifDecoder {
   private final byte[] d;
   private int p = 0;
   private int maxSide = 256;
   private long pixelBudget = 8000000L;

   private GifDecoder(byte[] var1) {
      this.d = var1;
   }

   static GifDecoder.Result decode(byte[] var0, int var1, int var2) throws IOException {
      GifDecoder var3 = new GifDecoder(var0);
      var3.maxSide = var2;
      return var3.run(var1);
   }

   private int u8() throws IOException {
      if (this.p >= this.d.length) {
         throw new IOException("Unexpected end of GIF");
      } else {
         return this.d[this.p++] & 0xFF;
      }
   }

   private int u16() throws IOException {
      return this.u8() | this.u8() << 8;
   }

   private int[] colorTable(int var1) throws IOException {
      int[] var2 = new int[var1];

      for (int var3 = 0; var3 < var1; var3++) {
         var2[var3] = 0xFF000000 | this.u8() << 16 | this.u8() << 8 | this.u8();
      }

      return var2;
   }

   private void skipSubBlocks() throws IOException {
      int var1;
      while ((var1 = this.u8()) != 0) {
         this.p += var1;
      }
   }

   private GifDecoder.Result run(int var1) throws IOException {
      if (this.d.length >= 13 && this.d[0] == 71 && this.d[1] == 73 && this.d[2] == 70) {
         this.p = 6;
         GifDecoder.Result var2 = new GifDecoder.Result();
         var2.width = this.u16();
         var2.height = this.u16();
         int var3 = this.u8();
         this.u8();
         this.u8();
         int[] var4 = (var3 & 128) != 0 ? this.colorTable(2 << (var3 & 7)) : null;
         int var5 = var2.width;
         int var6 = var2.height;
         if (var5 > 0 && var6 > 0 && (long)var5 * var6 <= 16000000L) {
            int[] var7 = new int[var5 * var6];
            float var8 = Math.min(1.0F, (float)this.maxSide / Math.max(var5, var6));
            var2.frameW = Math.max(1, Math.round(var5 * var8));
            var2.frameH = Math.max(1, Math.round(var6 * var8));
            long var9 = (long)var2.frameW * var2.frameH;
            byte var11 = 1;
            int var12 = 0;
            int var13 = 0;
            int var14 = 10;
            int var15 = -1;

            while (this.p < this.d.length && var12 < var1 * 8) {
               int var16 = this.u8();
               if (var16 == 59) {
                  break;
               }

               if (var16 == 33) {
                  int var34 = this.u8();
                  if (var34 == 249) {
                     this.u8();
                     int var35 = this.u8();
                     var13 = var35 >> 2 & 7;
                     var14 = this.u16();
                     int var36 = this.u8();
                     var15 = (var35 & 1) != 0 ? var36 : -1;
                     this.skipSubBlocks();
                  } else {
                     this.skipSubBlocks();
                  }
               } else {
                  if (var16 != 44) {
                     break;
                  }

                  int var17 = this.u16();
                  int var18 = this.u16();
                  int var19 = this.u16();
                  int var20 = this.u16();
                  int var21 = this.u8();
                  int[] var22 = (var21 & 128) != 0 ? this.colorTable(2 << (var21 & 7)) : var4;
                  boolean var23 = (var21 & 64) != 0;
                  int var24 = this.u8();
                  ByteArrayOutputStream var25 = new ByteArrayOutputStream();

                  int var26;
                  while ((var26 = this.u8()) != 0) {
                     if (this.p + var26 > this.d.length) {
                        throw new IOException("Truncated GIF");
                     }

                     var25.write(this.d, this.p, var26);
                     this.p += var26;
                  }

                  int[] var27 = lzw(var25.toByteArray(), var24, var19 * var20);
                  if (var23) {
                     var27 = deinterlace(var27, var19, var20);
                  }

                  int[] var28 = var13 == 3 ? (int[])var7.clone() : null;
                  if (var22 != null) {
                     for (int var29 = 0; var29 < var20; var29++) {
                        int var30 = var18 + var29;
                        if (var30 >= 0 && var30 < var6) {
                           for (int var31 = 0; var31 < var19; var31++) {
                              int var32 = var17 + var31;
                              if (var32 >= 0 && var32 < var5) {
                                 int var33 = var27[var29 * var19 + var31];
                                 if (var33 != var15 && var33 < var22.length) {
                                    var7[var30 * var5 + var32] = var22[var33];
                                 }
                              }
                           }
                        }
                     }
                  }

                  int var37 = var14 < 2 ? 100 : var14 * 10;
                  if (var12 % var11 == 0) {
                     var2.frames.add(scale(var7, var5, var6, var2.frameW, var2.frameH));
                     var2.delaysMs.add(var37);
                     if (var2.frames.size() * var9 > this.pixelBudget && var2.frames.size() > 1) {
                        ArrayList var38 = new ArrayList();
                        ArrayList var41 = new ArrayList();

                        for (byte var43 = 0; var43 < var2.frames.size(); var43 += 2) {
                           var38.add(var2.frames.get(var43));
                           var41.add(var2.delaysMs.get(var43) + (var43 + 1 < var2.delaysMs.size() ? var2.delaysMs.get(var43 + 1) : 0));
                        }

                        var2.frames.clear();
                        var2.frames.addAll(var38);
                        var2.delaysMs.clear();
                        var2.delaysMs.addAll(var41);
                        var11 *= 2;
                     }
                  } else {
                     int var39 = var2.delaysMs.size() - 1;
                     var2.delaysMs.set(var39, var2.delaysMs.get(var39) + var37);
                  }

                  var12++;
                  if (var13 == 2) {
                     for (int var40 = Math.max(0, var18); var40 < Math.min(var6, var18 + var20); var40++) {
                        for (int var42 = Math.max(0, var17); var42 < Math.min(var5, var17 + var19); var42++) {
                           var7[var40 * var5 + var42] = 0;
                        }
                     }
                  } else if (var13 == 3 && var28 != null) {
                     var7 = var28;
                  }

                  var13 = 0;
                  var14 = 10;
                  var15 = -1;
               }
            }

            if (var2.frames.isEmpty()) {
               throw new IOException("GIF has no frames");
            } else {
               return var2;
            }
         } else {
            throw new IOException("GIF is too large");
         }
      } else {
         throw new IOException("Not a GIF file");
      }
   }

   private static int[] scale(int[] var0, int var1, int var2, int var3, int var4) {
      if (var3 == var1 && var4 == var2) {
         return (int[])var0.clone();
      } else {
         int[] var5 = new int[var3 * var4];

         for (int var6 = 0; var6 < var4; var6++) {
            int var7 = Math.min(var2 - 1, (int)((var6 + 0.5F) * var2 / var4));

            for (int var8 = 0; var8 < var3; var8++) {
               int var9 = Math.min(var1 - 1, (int)((var8 + 0.5F) * var1 / var3));
               var5[var6 * var3 + var8] = var0[var7 * var1 + var9];
            }
         }

         return var5;
      }
   }

   private static int[] lzw(byte[] var0, int var1, int var2) {
      int[] var3 = new int[var2];
      int var4 = 1 << var1;
      int var5 = var4 + 1;
      int var6 = var1 + 1;
      int var7 = var4 + 2;
      int[] var8 = new int[4096];
      int[] var9 = new int[4096];
      int[] var10 = new int[4097];
      int var11 = 0;

      while (var11 < var4) {
         var9[var11] = var11++;
      }

      var11 = 0;
      int var12 = var0.length * 8;
      int var13 = -1;
      int var14 = 0;
      int var15 = 0;

      while (var15 < var2 && var11 + var6 <= var12) {
         int var16 = 0;

         for (int var17 = 0; var17 < var6; var17++) {
            int var18 = var0[var11 + var17 >> 3] >> (var11 + var17 & 7) & 1;
            var16 |= var18 << var17;
         }

         var11 += var6;
         if (var16 == var4) {
            var6 = var1 + 1;
            var7 = var4 + 2;
            var13 = -1;
         } else {
            if (var16 == var5) {
               break;
            }

            if (var13 == -1) {
               if (var16 >= var4) {
                  break;
               }

               var3[var15++] = var9[var16];
               var13 = var16;
               var14 = var16;
            } else {
               int var20 = var16;
               int var21 = 0;
               if (var16 >= var7) {
                  var10[var21++] = var14;
                  var16 = var13;
               }

               while (var16 >= var4 && var21 < 4096) {
                  var10[var21++] = var9[var16];
                  var16 = var8[var16];
               }

               var14 = var9[var16];
               var10[var21++] = var14;

               while (var21 > 0 && var15 < var2) {
                  var3[var15++] = var10[--var21];
               }

               if (var7 < 4096) {
                  var8[var7] = var13;
                  var9[var7] = var14;
                  if (++var7 == 1 << var6 && var6 < 12) {
                     var6++;
                  }
               }

               var13 = var20;
            }
         }
      }

      return var3;
   }

   private static int[] deinterlace(int[] var0, int var1, int var2) {
      int[] var3 = new int[var0.length];
      int var4 = 0;
      int[][] var5 = new int[][]{{0, 8}, {4, 8}, {2, 4}, {1, 2}};

      for (int[] var9 : var5) {
         for (int var10 = var9[0]; var10 < var2 && var4 < var2; var10 += var9[1]) {
            System.arraycopy(var0, var4 * var1, var3, var10 * var1, var1);
            var4++;
         }
      }

      return var3;
   }

   static final class Result {
      int width;
      int height;
      int frameW;
      int frameH;
      final List<int[]> frames = new ArrayList<>();
      final List<Integer> delaysMs = new ArrayList<>();
   }
}
